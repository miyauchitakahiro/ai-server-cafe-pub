package ai_server_cafe.gui;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public enum EnumVisibleType {
    ALWAYS(0, "Always"),
    HOME(1, "Home"),
    LOCAL_REFEREE(2, "Local Referee"),
    TEAM(3, "Team Settings"),
    NETWORK(4, "Network Settings"),
    VISIBILITY(5, "Visibility"),
    INFORMATION(6, "Team Information"),
    STATS(7, "Stats"),
    GAME_ANALYZE(8, "Analyze"),
    DEMO(9, "Demo"),
    DRIBBLESTATE(10, "Dribble State"),
    OBSTACLES(11, "Obstacles"),
    CLIP(12, "Clip"),
    RL_DRIBBLE(13, "RL Dribble");

    private final int id;
    private final String name;

    EnumVisibleType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public static EnumVisibleType getFromId(int id) {
        for (EnumVisibleType type : EnumVisibleType.values()) {
            if (type.id == id) {
                return type;
            }
        }
        return ALWAYS;
    }

    @Nonnull
    public static EnumVisibleType[] getExcludedAlways() {
        List<EnumVisibleType> result = new ArrayList<>();
        for (EnumVisibleType type : EnumVisibleType.values()) {
            if (type != ALWAYS) result.add(type);
        }
        return result.toArray(new EnumVisibleType[] {});
    }

    public String toString() {
        return this.name;
    }
}
