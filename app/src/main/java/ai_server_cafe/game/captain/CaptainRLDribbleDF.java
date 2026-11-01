package ai_server_cafe.game.captain;

import ai_server_cafe.game.formation.AbstractFormation;
import ai_server_cafe.game.formation.FormationRLDribbleDF;
import ai_server_cafe.model.game.EnumCaptainType;
import ai_server_cafe.model.game.EnumFormationType;
import ai_server_cafe.util.TeamColor;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * ドリブル強化学習 (守備側)
 * RL_DRIBBLEと反対のチームに設定する
 */
public class CaptainRLDribbleDF extends AbstractCaptain {
    public CaptainRLDribbleDF(EnumCaptainType type, TeamColor color) {
        super(type, color);
    }

    @Override
    protected void setFormationMap(@Nonnull Map<EnumFormationType, Class<? extends AbstractFormation>> map) {
        for (EnumFormationType type : EnumFormationType.values()) {
            if (type != EnumFormationType.HALT)
                map.put(type, FormationRLDribbleDF.class);
        }
    }

    @Override
    protected void setVelocityMap(@Nonnull Map<EnumFormationType, Double> map) {

    }
}
