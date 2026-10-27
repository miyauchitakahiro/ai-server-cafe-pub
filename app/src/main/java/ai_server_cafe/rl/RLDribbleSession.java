package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.config.EnumRLEnvironment;
import ai_server_cafe.config.EnumRLResetMode;
import ai_server_cafe.model.field.Field;
import ai_server_cafe.model.field.World;
import ai_server_cafe.network.radio.EnumRadioType;
import ai_server_cafe.network.transmitter.SimulatorTransmitter;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ボールに寄って向きを作りパス・シュートする強化学習のデータ収集を管理する
 * <p>
 * RL_DRIBBLEキャプテン側のActionRLDribbleから毎周期呼ばれ、
 * リセット(テレポート) → 静止待ち → エピソード実行 → キック後の結果判定 → 保存 を指定回数くり返す。
 * Formationは審判コマンドが変わるたびに作り直されるので状態はここ(シングルトン)に持つ。
 * <p>
 * 課題: 0.5m先のボールに寄って保持し、目標の方向を向いて蹴る。
 * パスはボールの軌道が目標の近くを通れば、シュートはゴールに入れば成功。
 * 蹴る前にボールを保持した地点から dribbleLimit (ルールの1m) を超えて運んだら失敗。
 * <p>
 * 行動は教師 (ActionKick) か、学習した方策 (DribblePolicy, actionSourceがPOLICY系のとき) が出す。
 * <p>
 * 座標について: onObservationに渡す状態はActionの座標系(invertされている場合あり、相手ゴールは+x側)。
 * テレポートと保存はフィールド座標系(invertなし)で行う。
 */
public final class RLDribbleSession {
    public enum Phase {
        IDLE,       // 収集していない
        RESETTING,  // 次の周期で初期配置を作る (テレポート、またはボール位置から開始位置を決める)
        APPROACHING,// FROM_BALL: ロボットが自走して開始位置へ向かう
        SETTLING,   // 開始位置で静止させて待つ
        RUNNING,    // エピソード実行中 (蹴るまで)
        EVALUATING, // 蹴った後、ボールの行方を見て成否を決める
        FINISHED    // 指定回数に達した、またはエラーで停止
    }

    /**
     * ActionRLDribbleがこの周期に出すべき指令
     */
    public enum Directive {
        HALT,   // 停止
        SETTLE, // その場でドリブラーを回して待つ
        MOVE,   // 開始位置へ移動する (ボールを避けて, getStartPose)
        ACT     // 行動を決めて onAction を呼ぶ
    }

    public enum EndReason {
        SUCCESS,            // パスが目標の近くを通った / シュートが入った
        MISSED,             // 蹴ったが外れた
        BALL_LOST,          // 蹴る前にボールが離れた
        OUT_OF_FIELD,       // 蹴る前にロボットかボールが場外に出た
        EXCESSIVE_DRIBBLE,  // 1m制限を超えて運んだ
        BALL_WANDERED,      // 蹴らないまま、ボールが初期位置から ballWanderLimit を超えて動いた (押し続けて追いかけた)
        TIMEOUT             // 時間内に蹴らなかった
    }

    private static RLDribbleSession instance = null;
    private static final Logger LOGGER = LogManager.getLogger("rl dribble");
    // 呼び出し間隔がこれより空いたら(Run停止・HALT・ロボット消失など)エピソードを破棄してやり直す
    private static final double MAX_CALL_GAP = 0.5;
    // テレポート後のロボット位置のずれの許容値 [mm]
    private static final double SPAWN_TOLERANCE = 150.0;
    // FROM_BALL: 開始位置に着いたとみなすロボットの位置・向き・速度の許容値 [mm, rad, mm/s]
    private static final double ARRIVE_DISTANCE = 50.0;
    private static final double ARRIVE_ANGLE = 0.1;
    private static final double ARRIVE_SPEED = 100.0;
    // FROM_BALL: ボールがこの速度未満なら止まっている、作ったときの位置からこれ以上動いたら作り直す [mm/s, mm]
    private static final double BALL_STILL_SPEED = 100.0;
    private static final double BALL_MOVED_DISTANCE = 100.0;
    // キック指令からこの時間内にボールが速くなったら蹴ったとみなす [s]
    private static final double KICK_DETECT_WINDOW = 0.3;
    // ボールが目標から最も近づいた距離よりこれだけ離れたら通り過ぎたとみなす [mm]
    private static final double PASSED_MARGIN = 100.0;
    // ボールがこの速度未満になったら止まったとみなす [mm/s]
    private static final double BALL_STOP_SPEED = 200.0;
    // 行の中の報酬・is_terminal列の位置
    private static final int COL_REWARD = DribbleObservation.OBS_DIM + DribbleObservation.ACTION_DIM;
    private static final int COL_IS_TERMINAL = COL_REWARD + 3;

    private Phase phase;
    private String status;
    private Optional<TeamColor> ownerColor;
    private int robotId;
    private Config config;
    private long baseSeed;
    private Optional<RLDataWriter> writer;
    private Optional<DribblePolicy> policy;   // actionSourceがPOLICY系のときだけ読み込む

    private int targetEpisodes;
    private int episodeIndex;
    private int successCount;
    private int resetAttempt;
    private long episodeSerial;
    private double lastCallTime;
    private double phaseStartTime;
    private Optional<DribbleScenario> scenario;

    // 実行中のエピソード
    private final List<float[]> rows;
    private double episodeStartTime;
    private double lastStepTime;
    private double resumeTime;          // この時刻まで次のテレポートを待つ
    private boolean waitingResume;      // 実機: 1回終わるごとに止めて、GUIのResumeを待つ
    private double lastKickCommandTime; // 最後にキック指令を出した時刻
    private Optional<Vector2D> dribbleStart; // ボールに触れ始めた地点 (フィールド座標系, 離れたらempty)
    private double episodeReturn;
    private Optional<PendingStep> pending;
    private double[] lastRecordedAction;
    // キック後の結果判定
    private Optional<float[]> kickRow;  // 蹴る直前のステップ (報酬に結果を足して保存する)
    private Optional<float[]> lastRow;  // 蹴った直後の観測 (最終行)
    private double minTargetDistance;   // 蹴った後にボールが目標に最も近づいた距離
    // 蹴った後のボールの様子 (episodes.csv に記録して、外れた原因を調べるのに使う)
    private double kickOffset;          // 蹴る直前のボールの横ずれ (ロボット座標系のy, 左が+) [mm]
    private double kickMaxSpeed;        // 蹴った後のボールの最高速度 [mm/s]
    private Optional<Vector2D> ballEndPos; // 結果が決まったときのボール位置 (フィールド座標系)
    private String evalEnd;             // 結果の決まり方 (GOAL, NEAR_TARGET, STOPPED, PASSED, OUT, TIME)

    /**
     * 観測済みで報酬がまだ決まっていないステップ
     */
    private static final class PendingStep {
        final float[] obs;
        final DribbleStepState state;
        final boolean invert;
        final boolean isFirst;
        final double time;
        double[] action;
        double[] expert;
        int teacher;

        PendingStep(float[] obs, DribbleStepState state, boolean invert, boolean isFirst, double time) {
            this.obs = obs;
            this.state = state;
            this.invert = invert;
            this.isFirst = isFirst;
            this.time = time;
            this.action = null;
            this.expert = null;
            this.teacher = -1;
        }
    }

    private RLDribbleSession() {
        this.phase = Phase.IDLE;
        this.status = "idle";
        this.ownerColor = Optional.empty();
        this.robotId = -1;
        this.config = ConfigManager.getInstance().getConfig();
        this.writer = Optional.empty();
        this.policy = Optional.empty();
        this.scenario = Optional.empty();
        this.dribbleStart = Optional.empty();
        this.rows = new ArrayList<>();
        this.pending = Optional.empty();
        this.kickRow = Optional.empty();
        this.lastRow = Optional.empty();
        this.ballEndPos = Optional.empty();
        this.evalEnd = "";
        this.lastRecordedAction = new double[DribbleObservation.ACTION_DIM];
    }

    public synchronized static RLDribbleSession getInstance() {
        if (instance == null) {
            instance = new RLDribbleSession();
        }
        return instance;
    }

    /* ============================== GUIから ============================== */

    /**
     * 設定された回数の自動収集を開始する
     */
    public synchronized void start() {
        if (this.phase != Phase.IDLE && this.phase != Phase.FINISHED) {
            LOGGER.warn("rl dribble collection is already running");
            return;
        }
        this.config = ConfigManager.getInstance().getConfig();
        // 環境ごとに必要な送信先とリセット方法を確かめる
        if (this.config.rlDribble.environment == EnumRLEnvironment.SIMULATOR) {
            if (!this.isRadioEnabled(EnumRadioType.SIMULATOR)) {
                this.finish("error: SIMULATOR transmitter (radioTypes=3) is not enabled");
                return;
            }
        } else {
            if (!this.isRadioEnabled(EnumRadioType.KIKS)) {
                this.finish("error: KIKS transmitter (radioTypes=0) is not enabled");
                return;
            }
            if (this.config.rlDribble.resetMode == EnumRLResetMode.TELEPORT) {
                this.finish("error: TELEPORT reset cannot be used on REAL robots (use FROM_BALL)");
                return;
            }
        }
        if (this.config.rlDribble.lostDistance <= DribbleScenario.getBallStartDistance(this.config)) {
            this.finish("error: lostDistance must be larger than ballStartDistance");
            return;
        }
        if (this.config.rlDribble.passMinDist > this.config.rlDribble.passMaxDist) {
            this.finish("error: passMinDist must not be larger than passMaxDist");
            return;
        }
        // 方策は開始のたびに読み直す (学習し直したONNXをそのまま使えるように)
        this.closePolicy();
        if (this.config.rlDribble.actionSource.usesPolicy()) {
            if (this.config.rlDribble.policyPath.isBlank()) {
                this.finish("error: policy (.onnx) path is empty");
                return;
            }
            try {
                this.policy = Optional.of(DribblePolicy.load(Paths.get(this.config.rlDribble.policyPath)));
                LOGGER.info("rl dribble policy loaded : {}", this.policy.get().getPath().toAbsolutePath());
            } catch (IOException e) {
                this.finish("error: " + e.getMessage());
                return;
            }
        }
        this.baseSeed = this.config.rlDribble.seed != 0 ? this.config.rlDribble.seed : System.currentTimeMillis();
        this.targetEpisodes = FastMath.max(1, this.config.rlDribble.targetEpisodes);
        this.episodeIndex = 0;
        this.successCount = 0;
        this.resetAttempt = 0;
        this.ownerColor = Optional.empty();
        this.lastCallTime = 0.0;
        this.resumeTime = 0.0;
        this.waitingResume = false;
        this.discardEpisode();
        this.writer = Optional.empty();
        this.scenario = Optional.empty();
        this.dribbleStart = Optional.empty();
        this.phase = Phase.RESETTING;
        this.status = "waiting for RLDribble captain";
        LOGGER.info("rl dribble collection started : {} episodes, seed {}", this.targetEpisodes, this.baseSeed);
    }

    /**
     * 収集を止める (実行中のエピソードは保存しない)
     */
    public synchronized void stop() {
        if (this.phase == Phase.IDLE)
            return;
        this.discardEpisode();
        this.closeWriter();
        this.phase = Phase.IDLE;
        this.status = String.format("stopped (%d episodes)", this.episodeIndex);
        LOGGER.info("rl dribble collection stopped");
    }

    /**
     * 実機で1回終わって止まっているのを再開する (GUIのResumeボタン)
     */
    public synchronized void resume() {
        if (!this.waitingResume)
            return;
        this.waitingResume = false;
        LOGGER.info("rl dribble resumed at episode {}", this.episodeIndex);
    }

    public synchronized boolean isWaitingResume() {
        return this.waitingResume;
    }

    public synchronized Phase getPhase() {
        return this.phase;
    }

    @Nonnull
    public synchronized String getStatusText() {
        if (this.phase == Phase.IDLE && this.episodeIndex == 0)
            return this.status;
        final double rate = this.episodeIndex == 0 ? 0.0 : 100.0 * this.successCount / this.episodeIndex;
        return String.format("%s  ep %d/%d  success %.0f%%", this.status, this.episodeIndex, this.targetEpisodes, rate);
    }

    @Nonnull
    public synchronized String getOutputText() {
        return this.writer.map(w -> w.getRunDir().toString()).orElse(this.config.rlDribble.saveData ? "-" : "(not saved)");
    }

    /* ============================== 制御ループから ============================== */

    /**
     * パス・シュートの目標地点
     * @param invert Actionの座標系がinvertされているか
     */
    @Nonnull
    public synchronized Optional<Vector2D> getTarget(boolean invert) {
        return this.scenario.map(s -> invert ? s.kickTarget().negate() : s.kickTarget());
    }

    /**
     * シュートか (falseならパス)
     */
    public synchronized boolean isShoot() {
        return this.scenario.map(DribbleScenario::shoot).orElse(false);
    }

    /**
     * 実行中のエピソードでボールを保持し始めた地点
     * @param invert Actionの座標系がinvertされているか
     */
    @Nonnull
    public synchronized Optional<Vector2D> getDribbleStart(boolean invert) {
        if (this.phase != Phase.RUNNING)
            return Optional.empty();
        return this.dribbleStart.map(p -> invert ? p.negate() : p);
    }

    /**
     * GUI表示用のドリブル開始地点 (フィールド座標系)
     */
    @Nonnull
    public synchronized Optional<Vector2D> getDisplayDribbleStart() {
        if (this.phase == Phase.IDLE)
            return Optional.empty();
        return this.dribbleStart;
    }

    /**
     * エピソードを開始するたびに増える番号 (Action側で教師やノイズを作り直すのに使う)
     */
    public synchronized long getEpisodeSerial() {
        return this.episodeSerial;
    }

    synchronized Optional<DribbleScenario> getScenario() {
        return this.scenario;
    }

    /**
     * GUI表示用の現在(最後)の初期配置 (フィールド座標系, invertなし)
     * 収集していないときはempty
     */
    @Nonnull
    public synchronized Optional<DribbleScenario> getDisplayScenario() {
        if (this.phase == Phase.IDLE)
            return Optional.empty();
        return this.scenario;
    }

    synchronized int getCompletedEpisodes() {
        return this.episodeIndex;
    }

    synchronized Optional<RLDataWriter> getWriter() {
        return this.writer;
    }

    /**
     * 学習した方策 (actionSourceがPOLICY系で収集中のときだけある)
     */
    @Nonnull
    public synchronized Optional<DribblePolicy> getPolicy() {
        return this.policy;
    }

    public synchronized long getEpisodeSeed() {
        return this.scenario.map(DribbleScenario::seed).orElse(this.baseSeed);
    }

    @Nonnull
    public synchronized Config.RLDribble getEpisodeConfig() {
        return this.config.rlDribble;
    }

    /**
     * 毎周期の観測
     *
     * @param world  Actionに渡されているworld
     * @param invert worldがinvertされているか
     * @param obs    DribbleObservation#build の結果
     * @param state  obsを作った状態
     */
    @Nonnull
    public synchronized Directive onObservation(@Nonnull TeamColor color, int id, @Nonnull World world, boolean invert,
                                                @Nonnull float[] obs, @Nonnull DribbleStepState state, double now) {
        if (this.phase == Phase.IDLE || this.phase == Phase.FINISHED)
            return Directive.HALT;
        if (this.ownerColor.isEmpty()) {
            this.ownerColor = Optional.of(color);
            if (!this.openWriter(color))
                return Directive.HALT;
        } else if (this.ownerColor.get() != color) {
            // 両チームがRLDribbleのときは先に呼んだ方だけ使う
            return Directive.HALT;
        }

        if (this.lastCallTime > 0.0 && now - this.lastCallTime > MAX_CALL_GAP
                && (this.phase == Phase.SETTLING || this.phase == Phase.RUNNING || this.phase == Phase.EVALUATING)) {
            LOGGER.info("rl dribble was interrupted, episode {} is discarded", this.episodeIndex);
            this.discardEpisode();
            this.phase = Phase.RESETTING;
        }
        if (this.robotId != id && this.phase != Phase.RESETTING) {
            this.discardEpisode();
            this.phase = Phase.RESETTING;
        }
        this.robotId = id;
        this.lastCallTime = now;

        switch (this.phase) {
            case RESETTING:
                return this.reset(color, id, world, invert, state, now);
            case APPROACHING:
                return this.approach(state, invert, now);
            case SETTLING:
                if (now - this.phaseStartTime < this.config.rlDribble.settleTime)
                    return Directive.SETTLE;
                if (!this.isSettled(state, invert)) {
                    this.resetAttempt++;
                    if (this.resetAttempt > this.config.rlDribble.maxResetRetries) {
                        this.finish("error: reset failed " + this.resetAttempt + " times");
                        return Directive.HALT;
                    }
                    LOGGER.debug("rl dribble reset failed, retry {}", this.resetAttempt);
                    this.phase = Phase.RESETTING;
                    return Directive.SETTLE;
                }
                this.beginEpisode(now);
                return this.step(obs, state, invert, now);
            case RUNNING:
                return this.step(obs, state, invert, now);
            case EVALUATING:
                return this.evaluate(state, world.getField(), invert, now);
            default:
                return Directive.HALT;
        }
    }

    /**
     * onObservationがACTを返した周期に実際に送った行動を記録する
     *
     * @param action  DribbleObservation#ACTION_NAMES の順
     * @param expert  同じ観測で教師 (ActionKick) が出す行動 (ノイズなし, DAggerのラベル)
     * @param teacher 行動を出した教師 (ActionRLDribble#TEACHER_KICK など)
     */
    public synchronized void onAction(@Nonnull TeamColor color, @Nonnull double[] action, @Nonnull double[] expert,
                                      int teacher) {
        if (this.phase != Phase.RUNNING || this.ownerColor.isEmpty() || this.ownerColor.get() != color)
            return;
        this.pending.ifPresent(p -> {
            p.action = action.clone();
            p.expert = expert.clone();
            p.teacher = teacher;
            if (action[DribbleObservation.ACTION_DIM - 1] > 0.0)
                this.lastKickCommandTime = p.time;
        });
    }

    /* ============================== 内部処理 ============================== */

    private Directive reset(TeamColor color, int id, World world, boolean invert, DribbleStepState state, double now) {
        this.discardEpisode();
        if (this.waitingResume) {
            // 実機: 人がボールやロボットの様子を確かめてからResumeで次へ進む
            this.status = "waiting for Resume";
            return Directive.HALT;
        }
        if (now < this.resumeTime) {
            // エピソード終了直後はしばらく停止させる
            this.status = "pausing";
            return Directive.HALT;
        }
        this.config = ConfigManager.getInstance().getConfig();
        // 使わないロボットをタッチラインの外へ (シミュレータのみ)
        List<Pair<TeamColor, Integer>> parked = new ArrayList<>();
        if (this.config.rlDribble.parkOtherRobots && this.config.rlDribble.environment == EnumRLEnvironment.SIMULATOR) {
            for (TeamColor c : TeamColor.values()) {
                world.getFriendlyRobotMap(c).keySet().stream().sorted().forEach(robotId -> {
                    if (c != color || robotId != id)
                        parked.add(new Pair<>(c, robotId));
                });
            }
        }
        final long seed = this.baseSeed + this.episodeIndex * 1000L + this.resetAttempt;
        // Actionの座標系では相手ゴールが+x側なので、invertされていればフィールド座標系では-x側
        final double goalSign = invert ? -1.0 : 1.0;
        if (this.config.rlDribble.resetMode == EnumRLResetMode.FROM_BALL)
            return this.resetFromBall(color, id, world, invert, state, parked, seed, goalSign, now);

        Optional<DribbleScenario> generated = DribbleScenario.generate(seed, world.getField(), this.config, goalSign);
        if (generated.isEmpty()) {
            this.finish("error: scenario cannot be generated (check margins and distances)");
            return Directive.HALT;
        }
        this.scenario = generated;
        this.dribbleStart = Optional.empty();
        SimulatorTransmitter.getInstance().sendControl(generated.get().buildTeleport(color, id, world.getField(), parked));
        this.phaseStartTime = now;
        this.phase = Phase.SETTLING;
        this.status = "settling";
        return Directive.SETTLE;
    }

    /**
     * 今のボール位置から初期配置を決め、ロボットを開始位置へ向かわせる (リセット方式 FROM_BALL)
     * ボールが止まっていない・使える範囲に無い・ほかのロボットがフィールドにいる (実機) ときは止めて待つ
     */
    private Directive resetFromBall(TeamColor color, int id, World world, boolean invert, DribbleStepState state,
                                    List<Pair<TeamColor, Integer>> parked, long seed, double goalSign, double now) {
        if (!parked.isEmpty())
            SimulatorTransmitter.getInstance().sendControl(DribbleScenario.buildPark(world.getField(), parked));
        // 実機ではほかのロボットを動かせないので、フィールドから出してもらうまで待つ (方策は相手のいない状況しか知らない)
        if (this.config.rlDribble.environment == EnumRLEnvironment.REAL && hasOtherRobotsInField(world, color, id)) {
            this.status = "waiting: remove other robots from the field";
            return Directive.HALT;
        }
        if (!state.ballVisible() || state.ballVel().getNorm() > BALL_STILL_SPEED) {
            this.status = "waiting for the ball to stop";
            return Directive.HALT;
        }
        final Vector2D ball = invert ? state.ballPos().negate() : state.ballPos();
        Optional<DribbleScenario> generated = DribbleScenario.generateFromBall(seed, world.getField(), this.config,
                goalSign, ball);
        if (generated.isEmpty()) {
            // 場外・壁際などにあるので人が戻すのを待つ (使える範囲はGUIに描く)
            this.status = "waiting: place the ball inside the area";
            return Directive.HALT;
        }
        this.scenario = generated;
        this.dribbleStart = Optional.empty();
        this.phaseStartTime = now;
        this.phase = Phase.APPROACHING;
        this.status = "approaching";
        return Directive.MOVE;
    }

    /**
     * 開始位置へ向かう (リセット方式 FROM_BALL)
     */
    private Directive approach(DribbleStepState state, boolean invert, double now) {
        final DribbleScenario sc = this.scenario.orElseThrow();
        final Vector2D ball = invert ? state.ballPos().negate() : state.ballPos();
        if (state.ballVisible() && MathHelper.distance2D(ball, sc.ballPos()) > BALL_MOVED_DISTANCE) {
            // 移動中にボールが動いた (触ってしまった等) ので作り直す
            this.phase = Phase.RESETTING;
            return Directive.HALT;
        }
        if (now - this.phaseStartTime > this.config.rlDribble.approachTimeout) {
            this.resetAttempt++;
            if (this.resetAttempt > this.config.rlDribble.maxResetRetries) {
                this.finish("error: could not reach the start position " + this.resetAttempt + " times");
                return Directive.HALT;
            }
            this.phase = Phase.RESETTING;
            return Directive.HALT;
        }
        final Vector2D robot = invert ? state.robotPos().negate() : state.robotPos();
        final double theta = invert ? MathHelper.wrapPI(state.robotTheta() + FastMath.PI) : state.robotTheta();
        if (MathHelper.distance2D(robot, sc.robotPos()) < ARRIVE_DISTANCE
                && MathHelper.inferiorAngle(theta, sc.robotTheta()) < ARRIVE_ANGLE
                && state.robotVel().getNorm() < ARRIVE_SPEED) {
            this.phaseStartTime = now;
            this.phase = Phase.SETTLING;
            this.status = "settling";
            return Directive.SETTLE;
        }
        return Directive.MOVE;
    }

    /**
     * 開始位置と向き (FROM_BALLで移動中のみ)
     * @param invert Actionの座標系がinvertされているか
     */
    @Nonnull
    public synchronized Optional<Pair<Vector2D, Double>> getStartPose(boolean invert) {
        if (this.phase != Phase.APPROACHING || this.scenario.isEmpty())
            return Optional.empty();
        final DribbleScenario sc = this.scenario.get();
        return Optional.of(invert ? new Pair<>(sc.robotPos().negate(), MathHelper.wrapPI(sc.robotTheta() + FastMath.PI))
                : new Pair<>(sc.robotPos(), sc.robotTheta()));
    }

    /**
     * 対象のロボット以外がフィールド内にいるか
     */
    private static boolean hasOtherRobotsInField(World world, TeamColor color, int id) {
        for (TeamColor c : TeamColor.values()) {
            for (var entry : world.getFriendlyRobotMap(c).entrySet()) {
                if (c == color && entry.getKey() == id)
                    continue;
                if (DribbleStepState.isInside(world.getField(), entry.getValue().getRobot().position()))
                    return true;
            }
        }
        return false;
    }

    private boolean isRadioEnabled(EnumRadioType radio) {
        for (int type : this.config.radioTypes) {
            if (type == radio.getId())
                return true;
        }
        return false;
    }

    private boolean isSettled(DribbleStepState state, boolean invert) {
        if (this.scenario.isEmpty())
            return false;
        final Vector2D robotPos = invert ? state.robotPos().negate() : state.robotPos();
        if (MathHelper.distance2D(robotPos, this.scenario.get().robotPos()) >= SPAWN_TOLERANCE)
            return false;
        if (DribbleScenario.isHoldingStart(this.config))
            return state.haveBall();
        // ボールが置いた位置に止まっているか
        final Vector2D ballPos = invert ? state.ballPos().negate() : state.ballPos();
        return state.ballVisible() && MathHelper.distance2D(ballPos, this.scenario.get().ballPos()) < SPAWN_TOLERANCE;
    }

    private void beginEpisode(double now) {
        this.discardEpisode();
        this.episodeStartTime = now;
        this.lastStepTime = now;
        this.lastKickCommandTime = Double.NEGATIVE_INFINITY;
        this.dribbleStart = Optional.empty();
        this.episodeReturn = 0.0;
        this.lastRecordedAction = new double[DribbleObservation.ACTION_DIM];
        this.resetAttempt = 0;
        this.episodeSerial++;
        this.phase = Phase.RUNNING;
        this.status = "running";
    }

    private Directive step(float[] obs, DribbleStepState state, boolean invert, double now) {
        final Config.RLDribble cfg = this.config.rlDribble;
        final double dt = now - this.lastStepTime;
        this.lastStepTime = now;

        // ドリブル開始地点: ボールに触れ始めた地点。離れたらリセットされ、また触れたらその地点から測る
        this.dribbleStart = state.dribbleStart().map(p -> invert ? p.negate() : p);

        // キック指令の直後にボールが速く動き出したら蹴ったとみなす
        final boolean kicked = now - this.lastKickCommandTime <= KICK_DETECT_WINDOW
                && state.ballVel().getNorm() > cfg.kickDetectSpeed;
        Optional<EndReason> reason = Optional.empty();
        if (!kicked) {
            if (state.dribbleDistance() > cfg.dribbleLimit)
                reason = Optional.of(EndReason.EXCESSIVE_DRIBBLE);
            else if (!state.inField())
                reason = Optional.of(EndReason.OUT_OF_FIELD);
            else if (cfg.ballWanderLimit > 0.0 && this.scenario.isPresent() && MathHelper.distance2D(
                    invert ? state.ballPos().negate() : state.ballPos(), this.scenario.get().ballPos()) > cfg.ballWanderLimit)
                reason = Optional.of(EndReason.BALL_WANDERED);
            else if (MathHelper.distance2D(state.robotPos(), state.ballPos()) > cfg.lostDistance)
                reason = Optional.of(EndReason.BALL_LOST);
            else if (now - this.episodeStartTime >= cfg.maxEpisodeTime)
                reason = Optional.of(EndReason.TIMEOUT);
        }

        // 1つ前のステップの報酬が決まったので記録する
        Optional<float[]> previousRow = Optional.empty();
        if (this.pending.isPresent()) {
            PendingStep p = this.pending.get();
            if (p.action == null) {
                // 行動が記録されなかった (ActionがACT以外を返した等) ので連続性が無い
                LOGGER.warn("rl dribble step has no action, episode {} is discarded", this.episodeIndex);
                this.discardEpisode();
                this.phase = Phase.RESETTING;
                return Directive.SETTLE;
            }
            double reward = RewardFunction.compute(cfg, p.state, state, p.action, this.lastRecordedAction, dt,
                    this.config.toFaceRadius + this.config.ballRadius);
            if (reason.isPresent())
                reward += RewardFunction.terminalBonus(cfg, reason.get(), 0.0);
            this.episodeReturn += reward;
            previousRow = Optional.of(this.makeRow(p.obs, p.action, reward, p.isFirst, false, false, p.expert, p.teacher, p.state,
                    p.invert, p.time - this.episodeStartTime));
            this.lastRecordedAction = p.action;
        }
        final Optional<DribbleStepState> prevState = this.pending.map(pp -> pp.state);
        this.pending = Optional.empty();

        final float[] finalRow = this.makeRow(obs, new double[DribbleObservation.ACTION_DIM], 0.0,
                previousRow.isEmpty() && this.rows.isEmpty(), true, true, new double[DribbleObservation.ACTION_DIM], -1, state, invert, now - this.episodeStartTime);
        if (kicked) {
            // 結果が出るまで蹴る直前のステップの報酬を保留する
            this.kickRow = previousRow;
            this.lastRow = Optional.of(finalRow);
            this.minTargetDistance = state.ballToTargetDistance();
            this.kickOffset = prevState.map(s -> s.toLocal(s.ballPos().subtract(s.robotPos())).getY()).orElse(0.0);
            this.kickMaxSpeed = state.ballVel().getNorm();
            this.phaseStartTime = now;
            this.phase = Phase.EVALUATING;
            this.status = "evaluating";
            return Directive.HALT;
        }
        previousRow.ifPresent(this.rows::add);
        if (reason.isPresent()) {
            // 時間切れは打ち切りなので終端ではない
            if (reason.get() == EndReason.TIMEOUT)
                finalRow[COL_IS_TERMINAL] = 0f;
            this.rows.add(finalRow);
            this.endEpisode(reason.get(), now);
            return Directive.HALT;
        }
        this.pending = Optional.of(new PendingStep(obs, state, invert, this.rows.isEmpty(), now));
        return Directive.ACT;
    }

    /**
     * 蹴った後のボールの行方から成否を決める
     */
    private Directive evaluate(DribbleStepState state, Field field, boolean invert, double now) {
        final Config.RLDribble cfg = this.config.rlDribble;
        final double dist = state.ballToTargetDistance();
        this.minTargetDistance = FastMath.min(this.minTargetDistance, dist);
        if (state.ballVisible())
            this.kickMaxSpeed = FastMath.max(this.kickMaxSpeed, state.ballVel().getNorm());
        final Vector2D ball = state.ballPos();
        // Actionの座標系では相手ゴールは+x側
        final boolean goal = ball.getX() > field.getMaxX() && FastMath.abs(ball.getY()) < field.getGoalWidth() / 2.0;
        final boolean outOfField = FastMath.abs(ball.getX()) > field.getMaxX() || FastMath.abs(ball.getY()) > field.getMaxY();
        final boolean passed = dist > this.minTargetDistance + PASSED_MARGIN;
        // 見えないボールの速度は0になっているので、止まったかは見えているときだけ判断する
        final boolean stopped = state.ballVisible() && state.ballVel().getNorm() < BALL_STOP_SPEED;
        final boolean timeUp = now - this.phaseStartTime >= cfg.evalMaxTime;

        Optional<Boolean> success = Optional.empty();
        if (state.shoot() ? goal : this.minTargetDistance < cfg.passSuccessRadius) {
            success = Optional.of(true);
            this.evalEnd = state.shoot() ? "GOAL" : "NEAR_TARGET";
        } else if (outOfField || passed || stopped || timeUp) {
            success = Optional.of(false);
            this.evalEnd = outOfField ? "OUT" : passed ? "PASSED" : stopped ? "STOPPED" : "TIME";
        }
        if (success.isEmpty())
            return Directive.HALT;
        this.ballEndPos = Optional.of(invert ? ball.negate() : ball);

        final EndReason reason = success.get() ? EndReason.SUCCESS : EndReason.MISSED;
        final double bonus = RewardFunction.terminalBonus(cfg, reason, this.minTargetDistance);
        this.kickRow.ifPresent(row -> {
            row[COL_REWARD] += (float) bonus;
            this.rows.add(row);
        });
        this.episodeReturn += bonus;
        this.lastRow.ifPresent(this.rows::add);
        this.endEpisode(reason, now);
        return Directive.HALT;
    }

    private float[] makeRow(float[] obs, double[] action, double reward, boolean isFirst, boolean isLast,
                            boolean isTerminal, double[] expert, int teacher, DribbleStepState s, boolean invert,
                            double time) {
        float[] row = new float[RLDataWriter.COLUMNS];
        int i = 0;
        for (float v : obs) row[i++] = v;
        for (double v : action) row[i++] = (float) v;
        row[i++] = (float) reward;
        row[i++] = isFirst ? 1f : 0f;
        row[i++] = isLast ? 1f : 0f;
        row[i++] = isTerminal ? 1f : 0f;
        row[i++] = teacher;
        for (double v : expert) row[i++] = (float) v;
        row[i++] = s.ballContact();
        row[i++] = s.ballVisible() ? 1f : 0f;
        final double sign = invert ? -1.0 : 1.0;
        row[i++] = (float) (sign * s.robotPos().getX() / 1000.0);
        row[i++] = (float) (sign * s.robotPos().getY() / 1000.0);
        row[i++] = (float) (invert ? MathHelper.wrapPI(s.robotTheta() + FastMath.PI) : s.robotTheta());
        row[i++] = (float) (sign * s.ballPos().getX() / 1000.0);
        row[i++] = (float) (sign * s.ballPos().getY() / 1000.0);
        row[i++] = (float) (sign * s.target().getX() / 1000.0);
        row[i++] = (float) (sign * s.target().getY() / 1000.0);
        row[i++] = s.shoot() ? 1f : 0f;
        row[i++] = (float) (s.dribbleDistance() / 1000.0);
        row[i++] = (float) time;
        return row;
    }

    private void endEpisode(EndReason reason, double now) {
        final boolean success = reason == EndReason.SUCCESS;
        if (success)
            this.successCount++;
        final long seed = this.getEpisodeSeed();
        final int index = this.episodeIndex;
        // 蹴っていなければ目標までの最短距離は無い
        final double minDistance = reason == EndReason.SUCCESS || reason == EndReason.MISSED ? this.minTargetDistance : -1.0;
        final boolean shoot = this.isShoot();
        // 蹴っていなければ蹴った後の記録は無い
        final boolean kicked = reason == EndReason.SUCCESS || reason == EndReason.MISSED;
        final RLDataWriter.KickResult kick = kicked
                ? new RLDataWriter.KickResult(this.kickOffset, this.kickMaxSpeed, this.ballEndPos, this.evalEnd)
                : RLDataWriter.KickResult.NONE;
        this.writer.ifPresent(w -> w.writeEpisode(index, this.rows, reason.name(), success, this.episodeReturn,
                now - this.episodeStartTime, seed, shoot, minDistance, kick));
        LOGGER.debug("rl dribble episode {} : {} ({} steps, return {})", index, reason, this.rows.size(), this.episodeReturn);
        this.episodeIndex++;
        this.discardEpisode();
        this.resumeTime = now + this.config.rlDribble.resetPause;
        if (this.episodeIndex >= this.targetEpisodes) {
            this.finish("finished");
            LOGGER.info("rl dribble collection finished : {} episodes, success {}", this.episodeIndex, this.successCount);
        } else {
            this.phase = Phase.RESETTING;
            // 実機では1回ごとに止めて、Resumeを押すまで次を始めない
            this.waitingResume = this.config.rlDribble.environment == EnumRLEnvironment.REAL;
        }
    }

    private void discardEpisode() {
        this.rows.clear();
        this.pending = Optional.empty();
        this.kickRow = Optional.empty();
        this.lastRow = Optional.empty();
        this.kickOffset = 0.0;
        this.kickMaxSpeed = 0.0;
        this.ballEndPos = Optional.empty();
        this.evalEnd = "";
    }

    private boolean openWriter(TeamColor color) {
        if (!this.config.rlDribble.saveData)
            return true;
        try {
            this.writer = Optional.of(RLDataWriter.create(this.config, this.baseSeed, color.name()));
            return true;
        } catch (IOException e) {
            this.finish("error: cannot create output directory");
            LOGGER.error("cannot create rl data directory : {}", e.getMessage());
            return false;
        }
    }

    private void closeWriter() {
        this.writer.ifPresent(RLDataWriter::close);
    }

    private void finish(String message) {
        this.discardEpisode();
        this.closeWriter();
        this.phase = Phase.FINISHED;
        this.status = message;
        if (message.startsWith("error"))
            LOGGER.error("rl dribble : {}", message);
    }

    private void closePolicy() {
        this.policy.ifPresent(DribblePolicy::close);
        this.policy = Optional.empty();
    }

    public static void reset() {
        if (instance != null) {
            instance.stop();
            instance.closePolicy();
        }
        instance = null;
    }
}
