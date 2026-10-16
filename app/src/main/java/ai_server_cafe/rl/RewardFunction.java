package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;

import javax.annotation.Nonnull;

/**
 * パス・シュートの報酬
 * 保存データには解析用のフィールド座標も残しているので、学習側で報酬を作り直すこともできる
 */
public final class RewardFunction {
    // 行動変化量を正規化するスケール (vx, vy [m/s], omega [rad/s], dribble, kick)
    private static final double[] ACTION_SCALE = {3.0, 3.0, 10.0, 15.0, 255.0};

    private RewardFunction() {}

    /**
     * 1ステップの報酬 (終端ボーナスを除く)
     *
     * @param prev       行動前の状態
     * @param next       行動後の状態
     * @param action     prevで選んだ行動
     * @param lastAction その1つ前の行動
     * @param dt         ステップ間隔 [s]
     * @param faceOffset ロボット中心からドリブラー前のボール位置までの距離 [mm]
     */
    public static double compute(@Nonnull Config.RLDribble cfg, @Nonnull DribbleStepState prev, @Nonnull DribbleStepState next,
                                 @Nonnull double[] action, @Nonnull double[] lastAction, double dt, double faceOffset) {
        // 保持していないときにドリブラーがボールに近づいた距離 [m]
        final double approach = prev.haveBall() ? 0.0
                : (faceToBall(prev, faceOffset) - faceToBall(next, faceOffset)) / 1000.0;
        // 保持しているときに目標方向へ向き直った角度 [rad]
        final double aim = prev.haveBall() && next.haveBall() ? prev.aimError() - next.aimError() : 0.0;
        double actionRate = 0.0;
        for (int i = 0; i < ACTION_SCALE.length; i++) {
            final double diff = (action[i] - lastAction[i]) / ACTION_SCALE[i];
            actionRate += diff * diff;
        }
        return cfg.approachWeight * approach + cfg.aimWeight * aim
                - cfg.timePenalty * dt - cfg.actionRatePenalty * actionRate;
    }

    private static double faceToBall(DribbleStepState s, double faceOffset) {
        final Vector2D face = s.robotPos().add(MathHelper.getFromPolar(faceOffset, s.robotTheta()));
        return MathHelper.distance2D(face, s.ballPos());
    }

    /**
     * エピソード終了時のボーナス
     *
     * @param missDistance 外したとき、ボールが目標に最も近づいた距離 [mm]
     */
    public static double terminalBonus(@Nonnull Config.RLDribble cfg, @Nonnull RLDribbleSession.EndReason reason,
                                       double missDistance) {
        return switch (reason) {
            case SUCCESS -> cfg.successReward;
            case MISSED -> -FastMath.min(cfg.failurePenalty, cfg.missWeight * missDistance / 1000.0);
            case BALL_LOST, OUT_OF_FIELD, EXCESSIVE_DRIBBLE -> -cfg.failurePenalty;
            case TIMEOUT -> 0.0;
        };
    }
}
