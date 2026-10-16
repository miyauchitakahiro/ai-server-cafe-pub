package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.model.field.Field;
import ai_server_cafe.network.proto.ssl.simulation.SslGcCommon;
import ai_server_cafe.network.proto.ssl.simulation.SslSimulationControl;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * 1エピソードの初期配置と課題 (フィールド座標系, invertなし)
 * ボールはロボットの正面 rlDribble.ballStartDistance の位置に置く。
 * パスならボールからランダムな距離・方向の地点、シュートなら相手ゴール内の地点を kickTarget にする。
 *
 * @param kickTarget パス・シュートの目標地点
 * @param shoot      シュートか (falseならパス)
 */
public record DribbleScenario(long seed, Vector2D robotPos, double robotTheta, Vector2D ballPos, Vector2D kickTarget,
                              boolean shoot) {
    // 待機ロボットを並べる間隔
    private static final double PARK_PITCH = 250.0;
    // 待機列の端からフィールドの角までの余白
    private static final double PARK_EDGE = 200.0;
    // ボールをドリブラーに接触させるときの隙間
    private static final double BALL_GAP = 5.0;
    // シュートのボール位置とペナルティエリアの余白
    private static final double PENALTY_MARGIN = 300.0;
    private static final int MAX_TRIALS = 200;

    /**
     * @param goalSign 相手ゴールがフィールド座標系の +x 側なら 1, -x 側なら -1
     */
    @Nonnull
    public static Optional<DribbleScenario> generate(long seed, @Nonnull Field field, @Nonnull Config config, double goalSign) {
        final Config.RLDribble cfg = config.rlDribble;
        // 連続したシードでも偏らないようにSplittableRandomを使う (java.util.Randomは最初の値が偏る)
        final SplittableRandom rng = new SplittableRandom(seed);
        final double minX = field.getMinX() + cfg.spawnMargin;
        final double maxX = field.getMaxX() - cfg.spawnMargin;
        final double minY = field.getMinY() + cfg.spawnMargin;
        final double maxY = field.getMaxY() - cfg.spawnMargin;
        if (minX >= maxX || minY >= maxY)
            return Optional.empty();

        final boolean shoot = rng.nextDouble() < cfg.shootRatio;
        final double ballDist = getBallStartDistance(config);
        final double sign = goalSign >= 0.0 ? 1.0 : -1.0;
        final Vector2D goal = new Vector2D(sign * field.getMaxX(), 0.0);
        // シュート時のボールの範囲 (ゴールからの距離で表す)
        final double shootNearX = field.getMaxX() - field.getPenaltyLength() - PENALTY_MARGIN;
        for (int i = 0; i < MAX_TRIALS; i++) {
            final double theta = uniform(rng, -FastMath.PI, FastMath.PI);
            final Vector2D ballPos;
            final Vector2D kickTarget;
            if (shoot) {
                ballPos = new Vector2D(sign * uniform(rng, field.getMaxX() - cfg.shootMaxDist, shootNearX), uniform(rng, minY, maxY));
                kickTarget = goal.add(new Vector2D(0.0,
                        uniform(rng, -0.5, 0.5) * cfg.shootAimWidth * field.getGoalWidth()));
                if (MathHelper.distance2D(ballPos, goal) > cfg.shootMaxDist)
                    continue;
            } else {
                ballPos = new Vector2D(uniform(rng, minX, maxX), uniform(rng, minY, maxY));
                kickTarget = ballPos.add(MathHelper.getFromPolar(uniform(rng, cfg.passMinDist, cfg.passMaxDist),
                        uniform(rng, -FastMath.PI, FastMath.PI)));
                if (!inside(kickTarget, minX, maxX, minY, maxY))
                    continue;
            }
            final Vector2D robotPos = ballPos.subtract(MathHelper.getFromPolar(ballDist, theta));
            if (inside(ballPos, minX, maxX, minY, maxY) && inside(robotPos, minX, maxX, minY, maxY))
                return Optional.of(new DribbleScenario(seed, robotPos, MathHelper.wrapPI(theta), ballPos, kickTarget, shoot));
        }
        return Optional.empty();
    }

    /**
     * 開始時のロボット中心からボールまでの距離
     * 設定がドリブラーに接する距離以下ならボールを持った状態から始める
     */
    public static double getBallStartDistance(@Nonnull Config config) {
        final double contact = config.toFaceRadius + config.ballRadius + BALL_GAP;
        return FastMath.max(config.rlDribble.ballStartDistance, contact);
    }

    /**
     * ボールを持った状態から始めるか
     */
    public static boolean isHoldingStart(@Nonnull Config config) {
        return config.rlDribble.ballStartDistance <= config.toFaceRadius + config.ballRadius + BALL_GAP;
    }

    /**
     * 初期配置へのテレポートと待機ロボットの整列をまとめたSimulatorControlを作る
     * 待機ロボットはタッチラインの外 (フィールド境界内) に並べるので、観測やプレーには関わらない
     *
     * @param parked 待機させるロボット (色, ID)
     */
    @Nonnull
    public SslSimulationControl.SimulatorControl buildTeleport(@Nonnull TeamColor color, int id, @Nonnull Field field,
                                                               @Nonnull List<Pair<TeamColor, Integer>> parked) {
        SslSimulationControl.SimulatorControl.Builder builder = SslSimulationControl.SimulatorControl.newBuilder();
        builder.setTeleportBall(SslSimulationControl.TeleportBall.newBuilder()
                .setX(toMeter(this.ballPos.getX())).setY(toMeter(this.ballPos.getY())).setZ(0f)
                .setVx(0f).setVy(0f).setVz(0f)
                .build());
        builder.addTeleportRobot(teleportRobot(color, id, this.robotPos, this.robotTheta));
        for (int i = 0; i < parked.size(); i++) {
            builder.addTeleportRobot(teleportRobot(parked.get(i).getFirst(), parked.get(i).getSecond(),
                    getParkPosition(field, i), FastMath.PI / 2.0));
        }
        return builder.build();
    }

    /**
     * i番目の待機ロボットの位置
     * 下側のタッチラインの外から並べ、入りきらなければ上側に並べる
     */
    @Nonnull
    public static Vector2D getParkPosition(@Nonnull Field field, int index) {
        final int perSide = FastMath.max(1, (int) ((field.getGameWidth() - 2.0 * PARK_EDGE) / PARK_PITCH) + 1);
        final double outside = field.getGameMargin() / 2.0;
        final double x = field.getMinX() + PARK_EDGE + (index % perSide) * PARK_PITCH;
        final double y = (index / perSide) % 2 == 0 ? field.getMinY() - outside : field.getMaxY() + outside;
        return new Vector2D(x, y);
    }

    private static SslSimulationControl.TeleportRobot teleportRobot(TeamColor color, int id, Vector2D pos, double theta) {
        return SslSimulationControl.TeleportRobot.newBuilder()
                .setId(SslGcCommon.RobotId.newBuilder().setId(id)
                        .setTeam(color.isYellow() ? SslGcCommon.Team.YELLOW : SslGcCommon.Team.BLUE))
                .setX(toMeter(pos.getX()))
                .setY(toMeter(pos.getY()))
                .setOrientation((float) theta)
                .setVX(0f)
                .setVY(0f)
                .setVAngular(0f)
                .build();
    }

    /**
     * 位置が指定された範囲内にあるかどうかを判定する
     * @param pos
     * @param minX
     * @param maxX
     * @param minY
     * @param maxY
     * @return
     */
    private static boolean inside(Vector2D pos, double minX, double maxX, double minY, double maxY) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY;
    }

    /**
     * [min, max)の範囲で一様分布に従う乱数を生成する
     */
    private static double uniform(SplittableRandom rng, double min, double max) {
        return min + (max - min) * rng.nextDouble();
    }

    private static float toMeter(double mm) {
        return (float) (mm / 1000.0);
    }
}
