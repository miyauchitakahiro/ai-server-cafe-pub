package ai_server_cafe.game.action;

import ai_server_cafe.config.Config;
import ai_server_cafe.controller.PIDController;
import ai_server_cafe.model.field.obstacle.base.ObstacleCircle;
import ai_server_cafe.model.game.Command;
import ai_server_cafe.rl.DribbleObservation;
import ai_server_cafe.rl.DribblePolicy;
import ai_server_cafe.rl.DribbleStepState;
import ai_server_cafe.rl.RLDribbleSession;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.util.EnumKickType;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.TimeHelper;
import ai_server_cafe.util.math.KickConverter;
import ai.onnxruntime.OrtException;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * パス・シュートの強化学習用のAction
 * 観測を作ってRLDribbleSessionに渡し、指示に従って行動を出す。
 * <p>
 * 行動の出どころは rlDribble.actionSource で選ぶ。
 * <ul>
 * <li>TEACHER系: 既存のActionKickを教師に使う (マニュアルのストレートキック)。
 * キックパワーはパスなら KickConverter.passSpeed、シュートなら rlDribble.shootSpeed から決める。</li>
 * <li>POLICY系: 学習した方策 (DribblePolicy) に観測を入れて行動を決める。
 * 方策のkickは蹴るかどうかの判断なので、蹴るときのパワーは教師と同じ式で決める。</li>
 * </ul>
 * どちらのときも教師は毎周期動かし、ノイズなしの教師の行動をラベルとして記録する (DAgger)。
 * 方策が動かしているときも記録するので、教師データに無い状態 (ボールを押し出した後など) で
 * 教師ならどうするかを学べる。
 * <p>
 * 行動はロボット座標系の速度・角速度・ドリブラー・キックパワーで表し、速度指令(targetVel + targetOmega)としてコントローラに渡す。
 * 教師が角度指令や位置指令を出したときはコントローラと同じ式で速度・角速度に直すので、実際の動きは変わらない。
 */
public class ActionRLDribble extends AbstractAction {
    // 保存データの teacher 列の値 (行動の出どころ)
    public static final int TEACHER_KICK = 0;
    public static final int TEACHER_POLICY = 1;
    private static final Logger LOGGER = LogManager.getLogger("action-rl-dribble");
    // 開始位置へ移動するとき、ボールの周りにこれだけ余白を取って避ける [mm] (開始位置はボールから0.5m)
    private static final double APPROACH_BALL_MARGIN = 250.0;
    // キックパワーの上限 (KickFlagの値)
    private static final int MAX_KICK_POWER = 255;

    private Vector2D target;
    private final ActionMove approachMove;
    private ActionKick kickTeacher;
    private long teacherSerial;
    private double[] prevAction;
    private int stableFrames;   // ボールを保持して中心にある状態が続いているフレーム数
    private final double[] noise;
    private SplittableRandom noiseRandom;
    // 角度指令・位置指令を速度に直すため (状態を持たない計算のみ使う)
    private PIDController commandConverter;
    // 速度・加速度の制限用 (直前の指令を状態として持つので、エピソードごとに作り直す)
    private PIDController limiter;

    public ActionRLDribble(int id, TeamColor color) {
        super(id, color);
        this.target = Vector2D.ZERO;
        this.approachMove = new ActionMove(id, color);
        this.kickTeacher = new ActionKick(id, color);
        this.teacherSerial = -1;
        this.prevAction = new double[DribbleObservation.ACTION_DIM];
        this.noise = new double[3];
        this.noiseRandom = new SplittableRandom();
        final Config config = ConfigManager.getInstance().getConfig();
        this.commandConverter = new PIDController(config.getCycleTime(), config.controllerConfig);
        this.limiter = new PIDController(config.getCycleTime(), config.controllerConfig);
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
            case MOVE: {
                this.prevAction = new double[DribbleObservation.ACTION_DIM];
                return this.moveToStart(session, state, config);
            }
            case SETTLE: {
                this.prevAction = new double[DribbleObservation.ACTION_DIM];
                return new Command().setTargetVel(Vector2D.ZERO).setTargetOmega(0.0)
                        .setDribble(session.getEpisodeConfig().settleDribble);
            }
            case ACT: {
                this.prepareEpisode(session);
                final Config.RLDribble cfg = session.getEpisodeConfig();
                final Optional<DribblePolicy> policy = session.getPolicy();
                // 教師 (ActionKick) は実際に動かすかどうかに関わらず毎周期動かして状態を進める
                final double[] expert = this.toAction(this.runKick(state, cfg), state);
                double[] action;
                int source;
                if (cfg.actionSource.usesPolicy() && policy.isPresent()) {
                    try {
                        action = policy.get().act(obs);
                        action[DribbleObservation.ACTION_DIM - 1] = action[DribbleObservation.ACTION_DIM - 1] >= 0.5
                                ? this.kickPower(state, cfg) : 0.0;
                    } catch (OrtException e) {
                        // 行動を記録しないのでこのエピソードはセッション側で破棄される
                        LOGGER.error("policy inference failed : {}", e.getMessage());
                        return new Command().setHalt(true);
                    }
                    source = TEACHER_POLICY;
                } else {
                    action = expert.clone();
                    source = TEACHER_KICK;
                }
                if (cfg.actionSource.addsNoise())
                    action = this.addNoise(action, cfg);
                // キックの条件: ボールを保持して中心にある状態が kickStableFrames 続くまでは蹴らずに待つ
                // (教師のラベルにも同じ条件をかけて、方策も「落ち着いてから蹴る」を学ぶようにする)
                this.stableFrames = nextStableFrames(this.stableFrames, state, cfg);
                if (!this.isKickAllowed(state, cfg)) {
                    waitInsteadOfKick(action, state);
                    waitInsteadOfKick(expert, state);
                }
                action = this.applyLimits(action, state);
                session.onAction(this.color, action, expert, source);
                this.prevAction = action;
                return this.toCommand(action, state);
            }
            default: {
                this.prevAction = new double[DribbleObservation.ACTION_DIM];
                return new Command().setHalt(true);
            }
        }
    }

    /**
     * 経路計画でボールを避けながら開始位置へ移動する (リセット方式 FROM_BALL)
     */
    private Command moveToStart(RLDribbleSession session, DribbleStepState state, Config config) {
        final Optional<Pair<Vector2D, Double>> pose = session.getStartPose(this.isInvert());
        if (pose.isEmpty())
            return new Command().setHalt(true);
        this.approachMove.setPos(pose.get().getFirst());
        this.approachMove.setAngle(pose.get().getSecond());
        // 障害物は毎周期作り直す (他のRoleと同じ使い方)
        WithPlanner<ActionMove> planner = new WithPlanner<>(this.approachMove, this.id, this.color,
                List.of(new ObstacleCircle(state.ballPos(), config.ballRadius, APPROACH_BALL_MARGIN)));
        // worldも開始位置もこのActionの座標系なので、反転はこのActionのupdateで1回だけかける (plannerには設定しない)
        planner.setWorld(this.world.get());
        return planner.update(this.now);
    }

    /**
     * ボールを保持して中心にある状態が続いたフレーム数を更新する
     */
    public static int nextStableFrames(int prev, DribbleStepState state, Config.RLDribble cfg) {
        return state.haveBall() && isBallCentered(state, cfg) ? prev + 1 : 0;
    }

    /**
     * 蹴ってよいか: ボールが中心にあり、kickStableFrames > 0 ならその状態が続いている
     * 実機では見えている位置と蹴る瞬間の位置がずれるので、ボールが落ち着くのを待ってから蹴る
     */
    private boolean isKickAllowed(DribbleStepState state, Config.RLDribble cfg) {
        return isBallCentered(state, cfg) && this.stableFrames >= cfg.kickStableFrames;
    }

    /**
     * 蹴りたいが条件を満たしていないとき: キックを止め、ボールを保持しているなら前へ押さない
     * (押し続けるとボールがこぼれて、追いかけたまま運んでしまうため)
     */
    private static void waitInsteadOfKick(double[] action, DribbleStepState state) {
        final int kick = DribbleObservation.ACTION_DIM - 1;
        if (action[kick] <= 0.0)
            return;
        action[kick] = 0.0;
        if (state.haveBall())
            action[0] = FastMath.min(action[0], 0.0);
    }

    /**
     * ボールがドリブラーの中心付近にあるか (横ずれが kickCenterTolerance 以内)
     * 実機ではドリブラーがボールを中心に寄せないので、ずれたまま蹴るとボールが斜めに飛ぶ
     */
    public static boolean isBallCentered(DribbleStepState state, Config.RLDribble cfg) {
        if (cfg.kickCenterTolerance <= 0.0)
            return true;
        final double offset = state.toLocal(state.ballPos().subtract(state.robotPos())).getY();
        return FastMath.abs(offset) <= cfg.kickCenterTolerance;
    }

    /**
     * 蹴るときのパワー (パスは目標までの距離から、シュートは rlDribble.shootSpeed から)
     */
    private int kickPower(DribbleStepState state, Config.RLDribble cfg) {
        final double speed = state.shoot() ? cfg.shootSpeed : KickConverter.passSpeed(state.ballPos(), this.target);
        return KickConverter.toPower(this.id, new Pair<>(EnumKickType.STRAIGHT, speed));
    }

    private Command runKick(DribbleStepState state, Config.RLDribble cfg) {
        this.kickTeacher.setWorld(this.world.get());
        this.kickTeacher.setTarget(this.target);
        this.kickTeacher.kickManually(new Pair<>(EnumKickType.STRAIGHT, this.kickPower(state, cfg)));
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
        this.stableFrames = 0;
        this.kickTeacher = new ActionKick(this.id, this.color);
        final Config config = ConfigManager.getInstance().getConfig();
        this.commandConverter = new PIDController(config.getCycleTime(), config.controllerConfig);
        this.limiter = new PIDController(config.getCycleTime(), config.controllerConfig);
        this.noiseRandom = new SplittableRandom(session.getEpisodeSeed());
        for (int i = 0; i < this.noise.length; i++)
            this.noise[i] = 0.0;
    }

    /**
     * vx, vy, omega の速度・加速度を、コントローラ (PIDController) と同じ式と Config (controllerConfig) の上限で制限する
     * velocityMax, velAngularMax, accelToTargetVelocity, brakeToTargetVelocity, accelToTargetVelAngular を使う。
     * 送った後にコントローラで切られる分を先に切っておくので、記録する行動と実際に送られる速度が一致する。
     */
    private double[] applyLimits(double[] action, DribbleStepState state) {
        // コントローラはフィールド座標系で制限するので、ロボット座標系の行動を変換してから通す
        final Vector2D fieldVel = state.toField(new Vector2D(action[0] * 1000.0, action[1] * 1000.0));
        final Vector3D limited = this.limiter.getVelClampedWithAccel(
                new Vector3D(fieldVel.getX(), fieldVel.getY(), action[2]));
        final Vector2D localVel = state.toLocal(new Vector2D(limited.getX(), limited.getY()));
        double[] result = action.clone();
        result[0] = localVel.getX() / 1000.0;
        result[1] = localVel.getY() / 1000.0;
        result[2] = limited.getZ();
        return result;
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
