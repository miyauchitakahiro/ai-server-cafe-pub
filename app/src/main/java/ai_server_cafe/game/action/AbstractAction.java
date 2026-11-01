package ai_server_cafe.game.action;

import ai_server_cafe.model.field.World;
import ai_server_cafe.model.game.Command;
import ai_server_cafe.updater.UpdaterCurrentObstacle;
import ai_server_cafe.util.TeamColor;

import java.util.ArrayList;
import java.util.Optional;

public abstract class AbstractAction {
    protected final int id;
    protected final TeamColor color;
    protected Optional<World> world;
    private boolean invert;
    protected long now;
    protected boolean isPenaltyKick;

    public AbstractAction(int id, TeamColor color) {
        this.id = id;
        this.color = color;
        this.invert = false;
        this.world = Optional.empty();
        this.now = 0L;
        this.isPenaltyKick = false;
    }

    public int getId() {
        return this.id;
    }

    public TeamColor getColor() {
        return this.color;
    }

    public void setInvert(boolean value) {
        this.invert = value;
    }

    protected boolean isInvert() {
        return this.invert;
    }

    public void setWorld(World world) {
        this.world = Optional.of(world);
    }

    public Command update(long now) {
        this.now = now;
        // 毎ループリセットするように
        this.isPenaltyKick = false;
        UpdaterCurrentObstacle.getInstance().put(this.color, this.id, new ArrayList<>());
        return this.invert ? this.execute().invert() : this.execute();
    }

    protected abstract Command execute();

    public abstract String getName();

    public abstract boolean isFinished();

    public void setPenaltyKick() {
        this.isPenaltyKick = true;
    }
}
