package ai_server_cafe.game.action;

import ai_server_cafe.config.Config;
import ai_server_cafe.config.EnumRLActionSource;
import ai_server_cafe.controller.PIDController;
import ai_server_cafe.model.game.Command;
import ai_server_cafe.rl.DribbleObservation;
import ai_server_cafe.rl.DribbleStepState;
import ai_server_cafe.rl.RLDribbleSession;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.util.EnumKickType;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.TimeHelper;
import ai_server_cafe.util.math.KickConverter;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import java.util.Optional;
import java.util.SplittableRandom;

/**
 * パス・シュートの強化学習用のAction
 * 観測を作ってRLDribbleSessionに渡し、指示に従って行動を出す。
 * <p>
 * 現在は行動の出どころとして既存のActionKickを教師に使う (マニュアルのストレートキック)。
 * キックパワーはパスなら KickConverter.passSpeed、シュートなら rlDribble.shootSpeed から決める。
 * <p>
 * 行動はロボット座標系の速度・角速度・ドリブラー・キックパワーで表し、速度指令(targetVel + targetOmega)としてコントローラに渡す。
 * 教師が角度指令や位置指令を出したときはコントローラと同じ式で速度・角速度に直すので、実際の動きは変わらない。
 */
public class ActionRLDribble extends AbstractAction {
    public static final int TEACHER_KICK = 0;
    // キックパワーの上限 (KickFlagの値)
    private static final int MAX_KICK_POWER = 255;

    private Vector2D target;
    private ActionKick kickTeacher;
    private long teacherSerial;
    private double[] prevAction;
    private final double[] noise;
    private SplittableRandom noiseRandom;
    // 角度指令・位置指令を速度に直すため (状態を持たない計算のみ使う)
    private PIDController commandConverter;

    public ActionRLDribble(int id, TeamColor color) {
        super(id, color);
        this.target = Vector2D.ZERO;
        this.kickTeacher = new ActionKick(id, color);
        this.teacherSerial = -1;
        this.prevAction = new double[DribbleObservation.ACTION_DIM];
        this.noise = new double[3];
        this.noiseRandom = new SplittableRandom();
        final Config config = ConfigManager.getInstance().getConfig();
        this.commandConverter = new PIDController(config.getCycleTime(), config.controllerConfig);
    }

    @Override
    protected Command execute() {
        if (this.world.isEmpty())
            return new Command().setHalt(true);
        final Config config = ConfigManager.getInstance().getConfig();
        final RLDribbleSession session = RLDribbleSession.getInstance();
        Optional<DribbleStepState> optState = DribbleStepState.measure(this.world.get(), this.color, this.id, this.target,
                session.isShoot(), session.getDribbleStart(this.isInvert()), config);
        if (optState.isEmpty())
            return new Command().setHalt(true);
        final DribbleStepState state = optState.get();
        final float[] obs = DribbleObservation.build(state, this.prevAction);

        final RLDribbleSession.Directive directive = session.onObservation(this.color, this.id, this.world.get(),
                this.isInvert(), obs, state, TimeHelper.now());
        switch (directive) {
            case SETTLE: {
                this.prevAction = new double[DribbleObservation.ACTION_DIM];
                return new Command().setTargetVel(Vector2D.ZERO).setTargetOmega(0.0)
                        .setDribble(session.getEpisodeConfig().settleDribble);
            }
            case ACT: {
                this.prepareEpisode(session);
                final Config.RLDribble cfg = session.getEpisodeConfig();
                final Command teacherCommand = this.runKick(state, cfg);

                double[] action = this.toAction(teacherCommand, state);
                if (cfg.actionSource == EnumRLActionSource.TEACHER_NOISE)
                    action = this.addNoise(action, cfg);
                session.onAction(this.color, action, TEACHER_KICK);
                this.prevAction = action;
                return this.toCommand(action, state);
            }
            default: {
                this.prevAction = new double[DribbleObservation.ACTION_DIM];
                return new Command().setHalt(true);
            }
        }
    }

    private Command runKick(DribbleStepState state, Config.RLDribble cfg) {
        final double speed = state.shoot() ? cfg.shootSpeed : KickConverter.passSpeed(state.ballPos(), this.target);
        final int power = KickConverter.toPower(this.id, new Pair<>(EnumKickType.STRAIGHT, speed));
        this.kickTeacher.setWorld(this.world.get());
        this.kickTeacher.setTarget(this.target);
        this.kickTeacher.kickManually(new Pair<>(EnumKickType.STRAIGHT, power));
        this.kickTeacher.setHoldTime(cfg.kickHoldTime);
        return this.kickTeacher.execute();
    }

    /**
     * 教師の指令を行動 (ロボット座標系の vx, vy [m/s], omega [rad/s], dribble, kick) にする
     * 位置指令・角度指令はコントローラ(PIDController)と同じ式で速度・角速度に直す
     */
    private double[] toAction(Command command, DribbleStepState state) {
        final Vector2D fieldVel = command.getTargetPos().isPresent()
                ? this.commandConverter.targetVelocity(command.getTargetPos().get(), state.robotPos(), state.robotVel())
                : command.getTargetVel();
        final double omega = command.getTargetTheta().isPresent()
                ? this.commandConverter.targetVelAngular(command.getTargetTheta().get(), state.robotTheta(), state.robotOmega())
                : command.getTargetOmega();
        final Vector2D localVel = state.toLocal(fieldVel);
        final double kick = command.getKickFlag().getFirst() == EnumKickType.NONE ? 0.0 : command.getKickFlag().getSecond();
        return new double[]{localVel.getX() / 1000.0, localVel.getY() / 1000.0, omega, command.getDribble(), kick};
    }

    /**
     * 新しいエピソードが始まっていたら教師とノイズを作り直す
     */
    private void prepareEpisode(RLDribbleSession session) {
        final long serial = session.getEpisodeSerial();
        if (serial == this.teacherSerial)
            return;
        this.teacherSerial = serial;
        this.kickTeacher = new ActionKick(this.id, this.color);
        final Config config = ConfigManager.getInstance().getConfig();
        this.commandConverter = new PIDController(config.getCycleTime(), config.controllerConfig);
        this.noiseRandom = new SplittableRandom(session.getEpisodeSeed());
        for (int i = 0; i < this.noise.length; i++)
            this.noise[i] = 0.0;
    }

    /**
     * vx, vy, omega にAR(1)相関のガウスノイズを足す (ドリブラーとキックはそのまま)
     */
    private double[] addNoise(double[] action, Config.RLDribble cfg) {
        final double rho = FastMath.min(FastMath.max(cfg.noiseCorrelation, 0.0), 0.999);
        // 定常分散がstd^2になるように駆動ノイズを縮める
        final double drive = FastMath.sqrt(1.0 - rho * rho);
        final double[] std = {cfg.noiseVelStd / 1000.0, cfg.noiseVelStd / 1000.0, cfg.noiseOmegaStd};
        double[] result = action.clone();
        for (int i = 0; i < this.noise.length; i++) {
            this.noise[i] = rho * this.noise[i] + drive * std[i] * this.noiseRandom.nextGaussian();
            result[i] += this.noise[i];
        }
        return result;
    }

    /**
     * ロボット座標系の行動を (このActionの座標系の) 速度指令にする
     * kickが1以上ならそのパワーでストレートキックする
     */
    private Command toCommand(double[] action, DribbleStepState state) {
        final Vector2D fieldVel = state.toField(new Vector2D(action[0] * 1000.0, action[1] * 1000.0));
        Command command = new Command().setTargetVel(fieldVel).setTargetOmega(action[2])
                .setDribble((int) FastMath.round(action[3]));
        final int kick = (int) FastMath.round(FastMath.min(action[4], MAX_KICK_POWER));
        if (kick >= 1)
            command.setKickFlag(EnumKickType.STRAIGHT, kick);
        return command;
    }

    /**
     * パス・シュートの目標地点 (このActionに渡されるworldの座標系)
     */
    public void setTarget(Vector2D target) {
        this.target = target;
    }

    @Override
    public String getName() {
        return "rl_dribble";
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
