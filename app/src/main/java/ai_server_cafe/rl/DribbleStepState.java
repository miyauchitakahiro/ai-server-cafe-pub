package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.model.field.Field;
import ai_server_cafe.model.field.FilteredBall;
import ai_server_cafe.model.field.FilteredRobot;
import ai_server_cafe.model.field.IntegratedRobot;
import ai_server_cafe.model.field.World;
import ai_server_cafe.updater.UpdaterRobotFeedback;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 1制御周期分のドリブルの状態
 * 座標は渡されたworldの座標系 (invertされている場合はinvert後) で mm, mm/s, rad
 *
 * @param target       パス・シュートの目標地点
 * @param shoot        シュートか (falseならパス)
 * @param dribbleStart ボールに触れ始めた地点 (触れていなければempty)
 * @param goalPos      相手ゴールの中心 (worldの座標系では+x側)
 * @param opponents    フィールド内の相手ロボット (近い順, 最大 DribbleObservation.MAX_OPPONENTS 台)
 * @param ballVisible  ボールがvisionで見えているか (見えていなければボールは最後に見えた位置)
 * @param haveBall     幾何的にボールを保持しているか
 * @param inField      ロボットとボールがフィールド(ゲームエリア)内か
 * @param ballContact  シミュレータのドリブラー接触フィードバック 1:接触 0:非接触 -1:不明
 */
public record DribbleStepState(
        Vector2D robotPos,
        double robotTheta,
        Vector2D robotVel,
        double robotOmega,
        Vector2D ballPos,
        Vector2D ballVel,
        Vector2D target,
        boolean shoot,
        Optional<Vector2D> dribbleStart,
        Vector2D goalPos,
        List<Opponent> opponents,
        boolean ballVisible,
        boolean haveBall,
        boolean inField,
        int ballContact
) {
    // フィードバックがこの秒数以上古ければ不明として扱う
    private static final double FEEDBACK_MAX_AGE = 0.2;

    public record Opponent(Vector2D pos, Vector2D vel) {}

    /**
     * worldから状態を計測する
     *
     * @param dribbleStart これまでのドリブル開始地点 (emptyで今ボールに触れていれば今のボール位置を開始地点にする)
     * @return 対象ロボットが見えていなければempty
     */
    @Nonnull
    public static Optional<DribbleStepState> measure(@Nonnull World world, @Nonnull TeamColor color, int id,
                                                     @Nonnull Vector2D target, boolean shoot,
                                                     @Nonnull Optional<Vector2D> dribbleStart, @Nonnull Config config) {
        if (!world.getFriendlyRobotMap(color).containsKey(id))
            return Optional.empty();
        final FilteredRobot robot = world.getFriendlyRobotMap(color).get(id).getRobot();
        final FilteredBall ball = world.getBall();
        final Vector2D robotPos = robot.position();
        final double theta = robot.getTheta();
        final Vector2D faceBallPos = robotPos.add(MathHelper.getFromPolar(config.toFaceRadius + config.ballRadius, theta));

        final boolean ballVisible = !ball.isLost();
        // 見えないとき (ロボットの影に入ったときなど) は最後に見えた位置を使う (教師のActionKickと同じ情報)
        // 正面にあるとみなすと、横や後ろで隠れたボールを保持していると取り違えるため
        // 速度は分からないので0にする
        final Vector2D ballPos = ball.position();
        final Vector2D ballVel = ballVisible ? ball.velocity() : Vector2D.ZERO;

        final boolean haveBall = MathHelper.distance2D(ballPos, faceBallPos) < config.rlDribble.holdDistance
                && MathHelper.inferiorAngle(theta, MathHelper.direction(ballPos, robotPos)) < config.rlDribble.holdAngle;

        final Field field = world.getField();
        final boolean inField = isInside(field, robotPos) && isInside(field, ballPos);

        // フィールド外 (待機中) のロボットはプレーに関わらないので含めない
        List<Opponent> opponents = new ArrayList<>();
        for (IntegratedRobot opp : world.getOppositeRobotMap(color).values()) {
            final FilteredRobot r = opp.getRobot();
            if (isInside(field, r.position()))
                opponents.add(new Opponent(r.position(), r.velocity()));
        }
        opponents.sort(Comparator.comparingDouble(o -> MathHelper.distance2D(o.pos(), robotPos)));
        if (opponents.size() > DribbleObservation.MAX_OPPONENTS)
            opponents = new ArrayList<>(opponents.subList(0, DribbleObservation.MAX_OPPONENTS));

        // ドリブル (1m制限) はボールに触れ続けている間だけ数える
        // ロボットとボールの距離がロボット半径 + ボール半径より大きければ離れたとみなして開始地点をリセットする
        // ボールが見えないとき (ロボットの影など) は位置が古く判断できないので、直前の状態を保つ
        final boolean touching = ballVisible
                ? MathHelper.distance2D(robotPos, ballPos) <= config.robotRadius + config.ballRadius
                : dribbleStart.isPresent();
        final Optional<Vector2D> start = !touching ? Optional.empty()
                : (dribbleStart.isPresent() ? dribbleStart : Optional.of(ballPos));

        final int ballContact = UpdaterRobotFeedback.getInstance().getBallContact(color, id, FEEDBACK_MAX_AGE);

        return Optional.of(new DribbleStepState(robotPos, theta, robot.velocity(), robot.getOmega(), ballPos, ballVel,
                target, shoot, start, new Vector2D(field.getMaxX(), 0.0), List.copyOf(opponents),
                ballVisible, haveBall, inField, ballContact));
    }

    /**
     * フィールド (ゲームエリア) 内か
     */
    static boolean isInside(@Nonnull Field field, @Nonnull Vector2D pos) {
        return FastMath.abs(pos.getX()) <= field.getMaxX() && FastMath.abs(pos.getY()) <= field.getMaxY();
    }

    public double ballToTargetDistance() {
        return MathHelper.distance2D(this.ballPos, this.target);
    }

    /**
     * ロボットの向きと、ロボットから目標への方向の差 [rad] (ActionKickと同じ基準)
     */
    public double aimError() {
        return MathHelper.inferiorAngle(this.robotTheta, MathHelper.direction(this.target, this.robotPos));
    }

    /**
     * ドリブル開始地点から今のボールまでの距離 (ボールに触れていなければ0)
     */
    public double dribbleDistance() {
        return this.dribbleStart.map(s -> MathHelper.distance2D(s, this.ballPos)).orElse(0.0);
    }

    /**
     * フィールド座標系のベクトルをロボット座標系 (x:前方, y:左方) に変換する
     */
    @Nonnull
    public Vector2D toLocal(@Nonnull Vector2D fieldVector) {
        return MathHelper.applyRotation2D(fieldVector, -this.robotTheta);
    }

    /**
     * ロボット座標系のベクトルをフィールド座標系に変換する
     */
    @Nonnull
    public Vector2D toField(@Nonnull Vector2D localVector) {
        return MathHelper.applyRotation2D(localVector, this.robotTheta);
    }
}
