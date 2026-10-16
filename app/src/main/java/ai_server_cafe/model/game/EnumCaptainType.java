package ai_server_cafe.model.game;

import ai_server_cafe.game.captain.AbstractCaptain;
import ai_server_cafe.game.captain.CaptainController;
import ai_server_cafe.game.captain.CaptainDemo;
import ai_server_cafe.game.captain.CaptainDemoController;
import ai_server_cafe.game.captain.CaptainFirst;
import ai_server_cafe.game.captain.CaptainHalt;
import ai_server_cafe.game.captain.CaptainKickRegulator;
import ai_server_cafe.game.captain.CaptainLocate;
import ai_server_cafe.game.captain.CaptainPlannerParameterRegulator;
import ai_server_cafe.game.captain.CaptainRLDribble;
import ai_server_cafe.game.captain.CaptainRLDribbleDF;
import ai_server_cafe.game.captain.CaptainTDribble;
import ai_server_cafe.game.captain.CaptainTReceive;
import ai_server_cafe.game.captain.CaptainTest;

public enum EnumCaptainType {
    NONE(0, "none", null),
    GAME(1, "game", CaptainFirst.class),
    TEST(2, "test",CaptainTest.class),
    CONTROLLER(3, "controller",CaptainController.class),
    LOCATE(4, "locate", CaptainLocate.class),
    T_RECEIVE(5, "t_receive", CaptainTReceive.class),
    DEMO(6, "demo", CaptainDemo.class),
    DEMOCONTROLLER(7, "demoController", CaptainDemoController.class),
    KICK_REGULATOR(8, "kick_regulator", CaptainKickRegulator.class),
    T_DRIBBLE(9, "t_dribble", CaptainTDribble.class),
    HALT(10, "halt", CaptainHalt.class),
    PP_REGULATOR(11, "pp_regulator", CaptainPlannerParameterRegulator.class),
    RL_DRIBBLE(12, "RLDribble", CaptainRLDribble.class),
    RL_DRIBBLE_DF(13, "RLDribble_DF", CaptainRLDribbleDF.class);

    private int idEnum;
    private String name;
    private Class<? extends AbstractCaptain> clazz;

    EnumCaptainType(int idEnum, String name, Class<? extends AbstractCaptain> clazz) {
        this.idEnum = idEnum;
        this.name = name;
        this.clazz = clazz;
    }

    public static EnumCaptainType getFromId(int id) {
        for (EnumCaptainType ert : EnumCaptainType.values()) {
            if (ert.getIdEnum() == id) {
                return ert;
            }
        }
        return NONE;
    }

    public int getIdEnum() {
        return this.idEnum;
    }

    public String getName() {
        return this.name;
    }

    public Class<? extends AbstractCaptain> getCaptainClass() {
        return this.clazz;
    }
}
