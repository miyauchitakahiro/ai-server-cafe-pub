package ai_server_cafe.updater;

import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.TimeHelper;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * シミュレータから返ってくるロボットのフィードバック (ssl_simulation_robot_feedback) を保持する
 */
public final class UpdaterRobotFeedback {
    public static final int CONTACT_UNKNOWN = -1;
    public static final int CONTACT_FALSE = 0;
    public static final int CONTACT_TRUE = 1;

    private static UpdaterRobotFeedback instance = null;
    private final Map<TeamColor, Map<Integer, Boolean>> contactMap;
    private final Map<TeamColor, Map<Integer, Double>> timeMap;

    private UpdaterRobotFeedback() {
        this.contactMap = new HashMap<>();
        this.timeMap = new HashMap<>();
        for (TeamColor color : TeamColor.values()) {
            this.contactMap.put(color, new HashMap<>());
            this.timeMap.put(color, new HashMap<>());
        }
    }

    public synchronized static UpdaterRobotFeedback getInstance() {
        if (instance == null) {
            instance = new UpdaterRobotFeedback();
        }
        return instance;
    }

    public synchronized void updateBallContact(@Nonnull TeamColor color, int id, boolean contact) {
        this.contactMap.get(color).put(id, contact);
        this.timeMap.get(color).put(id, TimeHelper.now());
    }

    /**
     * ドリブラーとボールの接触状態
     * @param maxAge この秒数より古いフィードバックは無いものとして扱う
     * @return CONTACT_TRUE / CONTACT_FALSE / CONTACT_UNKNOWN
     */
    public synchronized int getBallContact(@Nonnull TeamColor color, int id, double maxAge) {
        Double time = this.timeMap.get(color).get(id);
        if (time == null || TimeHelper.now() - time > maxAge)
            return CONTACT_UNKNOWN;
        return this.contactMap.get(color).get(id) ? CONTACT_TRUE : CONTACT_FALSE;
    }

    public synchronized static void reset() {
        instance = null;
    }
}
