package ai_server_cafe.game.role;

import ai_server_cafe.game.action.AbstractAction;
import ai_server_cafe.game.action.ActionRLDribble;
import ai_server_cafe.rl.RLDribbleSession;
import ai_server_cafe.util.TeamColor;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ドリブル強化学習のデータ収集を行うロボット (1台)
 * 目標位置はRLDribbleSessionが決める
 */
public class RoleRLDribbler extends AbstractRole {
    private final Map<Integer, ActionRLDribble> rlDribbleMap;

    public RoleRLDribbler(TeamColor color, int[] ids) {
        super(color, ids);
        this.rlDribbleMap = new HashMap<>();
        for (int id : ids) {
            this.rlDribbleMap.put(id, new ActionRLDribble(id, color));
        }
    }

    @Override
    protected List<? extends AbstractAction> execute() {
        List<AbstractAction> list = new ArrayList<>();
        if (this.world.isEmpty() || this.roleIds.isEmpty()) {
            return list;
        }
        this.visualizerTargets.clear();

        final int id = this.roleIds.getFirst();
        final ActionRLDribble action = this.rlDribbleMap.get(id);
        final Optional<Vector2D> target = RLDribbleSession.getInstance().getTarget(this.isInverse());
        if (target.isPresent()) {
            action.setTarget(target.get());
            this.visualizerTargets.add(target.get());
        }
        list.add(action);
        return list;
    }

    @Override
    public String getName() {
        return "rl_dribbler";
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
