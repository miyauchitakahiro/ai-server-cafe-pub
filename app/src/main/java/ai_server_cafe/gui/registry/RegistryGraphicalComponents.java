package ai_server_cafe.gui.registry;

import ai_server_cafe.config.Config;
import ai_server_cafe.gui.VisionArea;
import ai_server_cafe.gui.interfaces.IContainerCafe;
import ai_server_cafe.gui.interfaces.IGraphicalComponent;
import ai_server_cafe.gui.item.DrawBall;
import ai_server_cafe.gui.item.DrawField;
import ai_server_cafe.gui.item.DrawIntegratedRobots;
import ai_server_cafe.gui.item.DrawObstacles;
import ai_server_cafe.gui.item.DrawPaths;
import ai_server_cafe.gui.item.DrawReferee;
import ai_server_cafe.gui.item.DrawRLDribble;
import ai_server_cafe.gui.item.DrawScores;
import ai_server_cafe.gui.item.IItemDraw;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;


public final class RegistryGraphicalComponents {
    public static final IItemDraw BALL = new DrawBall();
    public static final IItemDraw FIELD = new DrawField();
    public static final IItemDraw ROBOTS = new DrawIntegratedRobots();
    public static final IItemDraw OBSTACLES = new DrawObstacles();
    public static final IItemDraw PATHS = new DrawPaths();
    public static final IItemDraw REFEREE = new DrawReferee();
    public static final IItemDraw SCORES = new DrawScores();
    public static final IItemDraw RL_DRIBBLE = new DrawRLDribble();
    // RL_DRIBBLEはロボットとボールの下に描く
    public static final List<IItemDraw> REGISTRY = List.of(FIELD, REFEREE, SCORES, RL_DRIBBLE, ROBOTS, PATHS, BALL, OBSTACLES);

    @Nonnull
    public static List<Class<? extends IContainerCafe>> getContainers() {
        return List.of(VisionArea.class);
    }

    /*
       ======================================== DO NOT TOUCH!!! =============================================
     */
    public static void init(Class<? extends IContainerCafe> area,
            LinkedHashMap<String, IGraphicalComponent> graphicalComponents) {
        for (IItemDraw draw : REGISTRY) {
            draw.init(area, graphicalComponents);
        }
    }

    public static void update(Class<? extends IContainerCafe> area,
            LinkedHashMap<String, IGraphicalComponent> graphicalComponents, Config config) {
        for (IItemDraw draw : REGISTRY) {
            draw.update(area, graphicalComponents, config);
        }
    }
}
