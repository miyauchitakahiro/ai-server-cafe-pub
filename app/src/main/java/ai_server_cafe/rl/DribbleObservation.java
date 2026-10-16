package ai_server_cafe.rl;

import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * ドリブル方策の観測と行動の定義
 * 観測はすべてロボット座標系 (x:前方, y:左方) で m, m/s, rad
 * フィールド座標系に依存しないのでチームのinvertやロボットの向きに関係なく同じ値になる
 * <p>
 * 学習側(Python)はこの値を再計算せず、Javaが記録した値をそのまま使う。
 * 定義を変えたら必ず VERSION を上げること。
 * <p>
 * 2: 蹴る方向・ドリブル開始地点・相手ゴール・相手ロボットを追加
 * 3: 目標をパス・シュートの目標地点にし、行動にキックを追加 (prev_kick, aim, is_shoot)
 */
public final class DribbleObservation {
    public static final int VERSION = 3;
    // 観測に含める相手ロボットの最大数 (足りない分は present=0 で埋める)
    public static final int MAX_OPPONENTS = 3;

    public static final String[] OBS_NAMES = buildNames();
    public static final int OBS_DIM = OBS_NAMES.length;

    /**
     * 行動: ロボット座標系の目標速度 [m/s], 目標角速度 [rad/s], ドリブラー出力 (Command#setDribbleの値),
     * キックパワー (ストレートキックのKickFlagの値, 0なら蹴らない)
     */
    public static final String[] ACTION_NAMES = {"vx", "vy", "omega", "dribble", "kick"};
    public static final int ACTION_DIM = ACTION_NAMES.length;

    private DribbleObservation() {}

    private static String[] buildNames() {
        List<String> names = new ArrayList<>(List.of(
                "ball_rel_x", "ball_rel_y",                 // ボール位置 - ロボット位置
                "ball_vel_rel_x", "ball_vel_rel_y",         // ボール速度 - ロボット速度
                "robot_vel_x", "robot_vel_y",               // ロボット速度
                "robot_omega",                              // ロボット角速度
                "target_rel_x", "target_rel_y",             // パス・シュート目標 - ロボット位置
                "ball_to_target_x", "ball_to_target_y",     // パス・シュート目標 - ボール位置
                "ball_to_target_dist",                      // ボールと目標の距離
                "have_ball",                                // 幾何的なボール保持 (0/1)
                "prev_vx", "prev_vy", "prev_omega", "prev_dribble", "prev_kick", // 直前の行動
                "aim_cos", "aim_sin",                       // ロボットから目標への方向 (ロボットの向きに対する角度)
                "is_shoot",                                 // シュートか (0:パス 1:シュート)
                "dribble_start_rel_x", "dribble_start_rel_y", // ドリブル開始地点 - ボール位置 (保持前は0)
                "dribbling",                                // ドリブル開始地点があるか (0/1)
                "dribble_dist",                             // ドリブル開始地点からボールまでの距離 (1m制限)
                "goal_rel_x", "goal_rel_y"                  // 相手ゴール中心 - ロボット位置
        ));
        for (int k = 0; k < MAX_OPPONENTS; k++) {
            names.add("opp" + k + "_present");              // 近い順にk番目の相手がいるか (0/1)
            names.add("opp" + k + "_rel_x");                // 相手位置 - ロボット位置
            names.add("opp" + k + "_rel_y");
            names.add("opp" + k + "_vel_rel_x");            // 相手速度 - ロボット速度
            names.add("opp" + k + "_vel_rel_y");
        }
        return names.toArray(new String[0]);
    }

    @Nonnull
    public static float[] build(@Nonnull DribbleStepState s, @Nonnull double[] prevAction) {
        final Vector2D ballRel = s.toLocal(s.ballPos().subtract(s.robotPos()));
        final Vector2D ballVelRel = s.toLocal(s.ballVel().subtract(s.robotVel()));
        final Vector2D robotVel = s.toLocal(s.robotVel());
        final Vector2D targetRel = s.toLocal(s.target().subtract(s.robotPos()));
        final Vector2D ballToTarget = s.toLocal(s.target().subtract(s.ballPos()));
        final double aimRel = MathHelper.direction(s.target(), s.robotPos()) - s.robotTheta();
        final Vector2D startRel = s.dribbleStart().map(p -> s.toLocal(p.subtract(s.ballPos()))).orElse(Vector2D.ZERO);
        final Vector2D goalRel = s.toLocal(s.goalPos().subtract(s.robotPos()));

        float[] obs = new float[OBS_DIM];
        int i = 0;
        obs[i++] = toMeter(ballRel.getX());
        obs[i++] = toMeter(ballRel.getY());
        obs[i++] = toMeter(ballVelRel.getX());
        obs[i++] = toMeter(ballVelRel.getY());
        obs[i++] = toMeter(robotVel.getX());
        obs[i++] = toMeter(robotVel.getY());
        obs[i++] = (float) s.robotOmega();
        obs[i++] = toMeter(targetRel.getX());
        obs[i++] = toMeter(targetRel.getY());
        obs[i++] = toMeter(ballToTarget.getX());
        obs[i++] = toMeter(ballToTarget.getY());
        obs[i++] = toMeter(s.ballToTargetDistance());
        obs[i++] = s.haveBall() ? 1.0f : 0.0f;
        for (int j = 0; j < ACTION_DIM; j++)
            obs[i++] = (float) prevAction[j];
        obs[i++] = (float) FastMath.cos(aimRel);
        obs[i++] = (float) FastMath.sin(aimRel);
        obs[i++] = s.shoot() ? 1.0f : 0.0f;
        obs[i++] = toMeter(startRel.getX());
        obs[i++] = toMeter(startRel.getY());
        obs[i++] = s.dribbleStart().isPresent() ? 1.0f : 0.0f;
        obs[i++] = toMeter(s.dribbleDistance());
        obs[i++] = toMeter(goalRel.getX());
        obs[i++] = toMeter(goalRel.getY());
        for (int k = 0; k < MAX_OPPONENTS; k++) {
            if (k < s.opponents().size()) {
                final DribbleStepState.Opponent o = s.opponents().get(k);
                final Vector2D rel = s.toLocal(o.pos().subtract(s.robotPos()));
                final Vector2D velRel = s.toLocal(o.vel().subtract(s.robotVel()));
                obs[i++] = 1.0f;
                obs[i++] = toMeter(rel.getX());
                obs[i++] = toMeter(rel.getY());
                obs[i++] = toMeter(velRel.getX());
                obs[i++] = toMeter(velRel.getY());
            } else {
                i += 5;
            }
        }
        return obs;
    }

    private static float toMeter(double value) {
        return (float) (value / 1000.0);
    }
}
