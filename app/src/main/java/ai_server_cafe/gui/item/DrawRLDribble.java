package ai_server_cafe.gui.item;

import ai_server_cafe.config.Config;
import ai_server_cafe.gui.VisionArea;
import ai_server_cafe.gui.interfaces.IContainerCafe;
import ai_server_cafe.gui.interfaces.IGraphicalComponent;
import ai_server_cafe.gui.item.basic.CircleCafe;
import ai_server_cafe.gui.item.basic.LineCafe;
import ai_server_cafe.gui.item.basic.NoneCafe;
import ai_server_cafe.gui.item.basic.StringCafe;
import ai_server_cafe.records.ver1.RecordData1;
import ai_server_cafe.rl.DribbleScenario;
import ai_server_cafe.rl.RLDribbleSession;
import ai_server_cafe.util.gui.ColorHelper;
import ai_server_cafe.util.math.MathHelper;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * パス・シュートの強化学習のエピソードの開始位置・キック目標・ドリブル制限範囲を描画する
 */
public class DrawRLDribble implements IItemDraw {
    // シュート目標の十字の大きさ [mm]
    private static final double SHOOT_MARK = 80.0;
    private static final List<String> KEYS = List.of("rl_dribble_path", "rl_dribble_start_robot",
            "rl_dribble_start_heading", "rl_dribble_start_ball", "rl_dribble_start_label", "rl_dribble_target",
            "rl_dribble_target_cross1", "rl_dribble_target_cross2", "rl_dribble_target_label", "rl_dribble_limit");

    @Override
    public void init(Class<? extends IContainerCafe> area,
            LinkedHashMap<String, IGraphicalComponent> graphicalComponents) {
        if (area != VisionArea.class) return;
        for (String key : KEYS) {
            graphicalComponents.putLast(key, new NoneCafe());
        }
    }

    @Override
    public void update(Class<? extends IContainerCafe> area,
            LinkedHashMap<String, IGraphicalComponent> graphicalComponents, Config config) {
        if (area != VisionArea.class) {
            return;
        }
        final RLDribbleSession session = RLDribbleSession.getInstance();
        Optional<DribbleScenario> scenario = session.getDisplayScenario();
        if (!config.rlDribble.drawScenario || scenario.isEmpty()) {
            for (String key : KEYS) {
                graphicalComponents.put(key, new NoneCafe());
            }
            return;
        }
        // シナリオはフィールド座標系なので表示の反転に合わせる
        final boolean invert = config.visibility.invertVisible;
        final Vector2D robotPos = flip(scenario.get().robotPos(), invert);
        final Vector2D ballPos = flip(scenario.get().ballPos(), invert);
        final Vector2D target = flip(scenario.get().kickTarget(), invert);
        final double theta = scenario.get().robotTheta() + (invert ? FastMath.PI : 0.0);
        final boolean shoot = scenario.get().shoot();

        // 開始位置: ロボットの外形と向き、ボール
        graphicalComponents.put("rl_dribble_path", new LineCafe(ballPos, target, ColorHelper.WEIGHT_VIOLET, 15.0F));
        graphicalComponents.put("rl_dribble_start_robot", new CircleCafe(ColorHelper.ARCHIVE_SKY,
                robotPos.getX(), robotPos.getY(), config.robotRadius, false, 20.0F));
        graphicalComponents.put("rl_dribble_start_heading", new LineCafe(robotPos,
                robotPos.add(MathHelper.getFromPolar(config.robotRadius, theta)), ColorHelper.ARCHIVE_SKY, 20.0F));
        graphicalComponents.put("rl_dribble_start_ball", new CircleCafe(ColorHelper.ARCHIVE_SKY,
                ballPos.getX(), ballPos.getY(), config.ballRadius, false, 15.0F));
        graphicalComponents.put("rl_dribble_start_label", new StringCafe(ColorHelper.ARCHIVE_SKY,
                (int) robotPos.getX() - 120, (int) robotPos.getY() + 160, "START", 120));

        // キック目標: パスは成功判定の半径、シュートはゴール内の狙う点
        final double radius = shoot ? SHOOT_MARK : config.rlDribble.passSuccessRadius;
        final double cross = shoot ? SHOOT_MARK : FastMath.max(config.rlDribble.passSuccessRadius * 0.7, 50.0);
        graphicalComponents.put("rl_dribble_target", shoot ? new NoneCafe() : new CircleCafe(ColorHelper.TARGET_EMERALD,
                target.getX(), target.getY(), radius, false, 20.0F));
        graphicalComponents.put("rl_dribble_target_cross1", new LineCafe(target.getX() - cross, target.getY() - cross,
                target.getX() + cross, target.getY() + cross, ColorHelper.TARGET_EMERALD, 15.0F));
        graphicalComponents.put("rl_dribble_target_cross2", new LineCafe(target.getX() - cross, target.getY() + cross,
                target.getX() + cross, target.getY() - cross, ColorHelper.TARGET_EMERALD, 15.0F));
        graphicalComponents.put("rl_dribble_target_label", new StringCafe(ColorHelper.TARGET_EMERALD,
                (int) target.getX() - 140, (int) target.getY() + 200, shoot ? "SHOOT" : "PASS", 120));

        // ドリブル制限 (保持した地点、保持前はボールの初期位置から dribbleLimit)
        final Vector2D limitCenter = session.getDisplayDribbleStart().map(p -> flip(p, invert)).orElse(ballPos);
        graphicalComponents.put("rl_dribble_limit", new CircleCafe(ColorHelper.ROLE_ORANGE,
                limitCenter.getX(), limitCenter.getY(), config.rlDribble.dribbleLimit, false, 10.0F));
    }

    private static Vector2D flip(Vector2D pos, boolean invert) {
        return invert ? pos.negate() : pos;
    }

    @Override
    public RecordData1 putRecord(@NotNull RecordData1 layer) {
        // 収集データ側に初期配置が残るのでレイヤーには保存しない
        return layer;
    }
}
