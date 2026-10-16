package ai_server_cafe.game.formation;

import ai_server_cafe.game.role.AbstractRole;
import ai_server_cafe.util.TeamColor;

import java.util.ArrayList;
import java.util.List;

/**
 * ドリブル強化学習 (守備側)
 * 「相手なし」の段階では全ロボットを停止させておく。
 * 配置はRLDribbleSessionがリセット時にフィールド下端へテレポートする。
 * 停止指令を送り続けることで、シミュレータ上で直前の速度指令のまま動き続けるのを防ぐ。
 * 相手ありの段階ではここに守備ロールを追加する。
 */
public class FormationRLDribbleDF extends AbstractFormation {

    public FormationRLDribbleDF(TeamColor color, int[] ids) {
        super(color, ids);
    }

    @Override
    protected List<? extends AbstractRole> execute() {
        List<AbstractRole> list = new ArrayList<>();
        if (this.world.isEmpty())
            return list;
        this.roleHalt.setRoleIds(getVisibleIds(this.world.get(), this.color, this.activeRobots));
        list.add(this.roleHalt);
        return list;
    }

    @Override
    public String getName() {
        return "rl_dribble_df";
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
