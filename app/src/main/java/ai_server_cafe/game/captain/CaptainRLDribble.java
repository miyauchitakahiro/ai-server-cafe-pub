package ai_server_cafe.game.captain;

import ai_server_cafe.game.formation.AbstractFormation;
import ai_server_cafe.game.formation.FormationRLDribble;
import ai_server_cafe.model.game.EnumCaptainType;
import ai_server_cafe.model.game.EnumFormationType;
import ai_server_cafe.util.TeamColor;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * ドリブル強化学習 (ドリブルする側)
 * 審判コマンドがHALT以外なら常にFormationRLDribbleを使う
 * 相手チームにはRL_DRIBBLE_DFを設定する
 */
public class CaptainRLDribble extends AbstractCaptain {
    public CaptainRLDribble(EnumCaptainType type, TeamColor color) {
        super(type, color);
    }

    @Override
    protected void setFormationMap(@Nonnull Map<EnumFormationType, Class<? extends AbstractFormation>> map) {
        for (EnumFormationType type : EnumFormationType.values()) {
            if (type != EnumFormationType.HALT)
                map.put(type, FormationRLDribble.class);
        }
    }

    @Override
    protected void setVelocityMap(@Nonnull Map<EnumFormationType, Double> map) {

    }
}
