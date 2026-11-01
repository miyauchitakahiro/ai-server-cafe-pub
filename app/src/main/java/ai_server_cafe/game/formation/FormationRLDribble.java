package ai_server_cafe.game.formation;

import ai_server_cafe.game.role.AbstractRole;
import ai_server_cafe.game.role.RoleRLDribbler;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.util.TeamColor;

import java.util.ArrayList;
import java.util.List;

/**
 * ドリブル強化学習 (ドリブルする側)
 * config.rlDribble.robotId のロボット (見えていなければ見えている最初のロボット) がドリブルし、残りは停止する
 */
public class FormationRLDribble extends AbstractFormation {
    protected RoleRLDribbler roleRLDribbler;

    public FormationRLDribble(TeamColor color, int[] ids) {
        super(color, ids);
        this.roleRLDribbler = new RoleRLDribbler(color, ids);
    }

    @Override
    protected List<? extends AbstractRole> execute() {
        List<AbstractRole> list = new ArrayList<>();
        if (this.world.isEmpty())
            return list;
        List<Integer> visibleIds = getVisibleIds(this.world.get(), this.color, this.activeRobots);
        if (visibleIds.isEmpty())
            return list;
        final int configId = ConfigManager.getInstance().getConfig().rlDribble.robotId;
        final int dribbler = visibleIds.contains(configId) ? configId : visibleIds.getFirst();
        List<Integer> dribblerIds = new ArrayList<>();
        List<Integer> haltIds = new ArrayList<>();
        for (int id : visibleIds) {
            if (id == dribbler)
                dribblerIds.add(id);
            else
                haltIds.add(id);
        }
        this.roleRLDribbler.setRoleIds(dribblerIds);
        this.roleHalt.setRoleIds(haltIds);
        list.add(this.roleRLDribbler);
        list.add(this.roleHalt);
        return list;
    }

    @Override
    public String getName() {
        return "rl_dribble";
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
