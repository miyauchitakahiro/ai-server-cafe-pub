package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.config.EnumRLActionSource;
import ai_server_cafe.game.action.ActionRLDribble;
import ai_server_cafe.gui.VisionArea;
import ai_server_cafe.gui.interfaces.IGraphicalComponent;
import ai_server_cafe.gui.item.DrawRLDribble;
import ai_server_cafe.gui.item.basic.CircleCafe;
import ai_server_cafe.gui.item.basic.LineCafe;
import ai_server_cafe.gui.item.basic.NoneCafe;
import ai_server_cafe.model.field.Field;
import ai_server_cafe.model.field.FilteredBall;
import ai_server_cafe.model.field.FilteredRobot;
import ai_server_cafe.model.field.IntegratedRobot;
import ai_server_cafe.model.field.World;
import ai_server_cafe.model.game.Command;
import ai_server_cafe.network.proto.ssl.simulation.SslSimulationControl;
import ai_server_cafe.network.radio.EnumRadioType;
import ai_server_cafe.updater.ConfigManager;
import ai_server_cafe.util.TeamColor;
import ai_server_cafe.util.interfaces.WrapperCloneableMap;
import ai_server_cafe.util.math.MathHelper;
import ai.onnxruntime.OrtException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestRLDribble {
    private static final double CYCLE = 1.0 / 60.0;
    private static final List<String> OBS = Arrays.asList(DribbleObservation.OBS_NAMES);

    private int[] savedRadioTypes;
    private Config.RLDribble savedRLDribble;

    @BeforeEach
    void setUp() {
        Config editable = ConfigManager.getInstance().getEditableConfig();
        this.savedRadioTypes = editable.radioTypes;
        this.savedRLDribble = editable.rlDribble.clone();
        // テストはconfigファイルの内容に依存させない
        editable.rlDribble = new Config.RLDribble();
        editable.radioTypes = new int[]{EnumRadioType.SIMULATOR.getId()};
    }

    @AfterEach
    void tearDown() {
        RLDribbleSession.reset();
        Config editable = ConfigManager.getInstance().getEditableConfig();
        editable.radioTypes = this.savedRadioTypes;
        editable.rlDribble = this.savedRLDribble;
    }

    private static Config.RLDribble rl() {
        return ConfigManager.getInstance().getEditableConfig().rlDribble;
    }

    private static Config config() {
        return ConfigManager.getInstance().getConfig();
    }

    /* ============================== テスト用のworld ============================== */

    private static World world(Vector2D robotPos, double theta, Vector2D robotVel, Vector2D ballPos, Vector2D ballVel,
                               List<Vector2D> opponents) {
        FilteredRobot robot = new FilteredRobot();
        robot.setX(robotPos.getX());
        robot.setY(robotPos.getY());
        robot.setTheta(theta);
        robot.setVx(robotVel.getX());
        robot.setVy(robotVel.getY());
        FilteredBall ball = new FilteredBall();
        ball.setX(ballPos.getX());
        ball.setY(ballPos.getY());
        ball.setVx(ballVel.getX());
        ball.setVy(ballVel.getY());
        ball.setLost(false);
        Map<Integer, IntegratedRobot> blue = new HashMap<>();
        blue.put(3, new IntegratedRobot(3, TeamColor.BLUE, robot, Optional.empty(), Optional.empty(), Optional.empty()));
        Map<Integer, IntegratedRobot> yellow = new HashMap<>();
        for (int i = 0; i < opponents.size(); i++) {
            FilteredRobot opp = new FilteredRobot();
            opp.setX(opponents.get(i).getX());
            opp.setY(opponents.get(i).getY());
            yellow.put(i, new IntegratedRobot(i, TeamColor.YELLOW, opp, Optional.empty(), Optional.empty(), Optional.empty()));
        }
        return new World(new WrapperCloneableMap<>(blue), new WrapperCloneableMap<>(yellow), ball, new Field(),
                Vector2D.ZERO, Vector2D.ZERO, false, false);
    }

    private static World world(Vector2D robotPos, double theta, Vector2D ballPos) {
        return world(robotPos, theta, Vector2D.ZERO, ballPos, Vector2D.ZERO, List.of());
    }

    private static double contact() {
        return config().toFaceRadius + config().ballRadius;
    }

    private static Vector2D facePos(Vector2D robotPos, double theta) {
        return robotPos.add(MathHelper.getFromPolar(contact(), theta));
    }

    /**
     * 1周期進める。ACTなら行動を記録する
     */
    private static RLDribbleSession.Directive step(RLDribbleSession session, World w, double now, double kick) {
        DribbleStepState s = DribbleStepState.measure(w, TeamColor.BLUE, 3, session.getTarget(false).orElse(Vector2D.ZERO),
                session.isShoot(), session.getDribbleStart(false), config()).orElseThrow();
        RLDribbleSession.Directive d = session.onObservation(TeamColor.BLUE, 3, w, false,
                DribbleObservation.build(s, new double[DribbleObservation.ACTION_DIM]), s, now);
        if (d == RLDribbleSession.Directive.ACT)
            session.onAction(TeamColor.BLUE, new double[]{1.0, 0.0, 0.0, 12, kick}, new double[]{0.5, 0.0, 0.0, 12, kick},
                    ActionRLDribble.TEACHER_KICK);
        return d;
    }

    private static RLDribbleSession.Directive step(RLDribbleSession session, World w, double now) {
        return step(session, w, now, 0.0);
    }

    /**
     * 簡単な運動モデル: 正面のボールに寄って保持し、ボールを中心に回って aim の方向を向き、蹴る
     */
    private static final class Mover {
        final double speed = 1500.0;
        final double turnRate = 4.0;
        final double kickSpeed = 4000.0;
        Vector2D robotPos;
        double theta;
        Vector2D ballPos;
        Vector2D robotVel = Vector2D.ZERO;
        Vector2D ballVel = Vector2D.ZERO;
        boolean held = false;
        boolean kicked = false;

        Mover(DribbleScenario s) {
            this.robotPos = s.robotPos();
            this.theta = s.robotTheta();
            this.ballPos = s.ballPos();
        }

        /**
         * @param aim 向く方向
         * @return この周期にキック指令を出すか
         */
        boolean move(double aim) {
            if (this.kicked) {
                this.robotVel = Vector2D.ZERO;
                this.ballPos = this.ballPos.add(this.ballVel.scalarMultiply(CYCLE));
                return false;
            }
            if (!this.held) {
                this.robotVel = MathHelper.getFromPolar(this.speed, this.theta);
                this.robotPos = this.robotPos.add(this.robotVel.scalarMultiply(CYCLE));
                if (MathHelper.distance2D(this.robotPos, this.ballPos) <= contact()) {
                    this.held = true;
                    this.robotVel = Vector2D.ZERO;
                    this.robotPos = this.ballPos.subtract(MathHelper.getFromPolar(contact(), this.theta));
                }
                return false;
            }
            // ボールを中心に回る
            final double diff = MathHelper.wrapPI(aim - this.theta);
            this.theta += FastMath.copySign(FastMath.min(FastMath.abs(diff), this.turnRate * CYCLE), diff);
            this.robotPos = this.ballPos.subtract(MathHelper.getFromPolar(contact(), this.theta));
            return FastMath.abs(diff) < 0.02;
        }

        void kick() {
            this.kicked = true;
            this.ballVel = MathHelper.getFromPolar(this.kickSpeed, this.theta);
        }

        World world() {
            return TestRLDribble.world(this.robotPos, this.theta, this.robotVel, this.ballPos,
                    this.kicked ? this.ballVel : (this.held ? this.robotVel : Vector2D.ZERO), List.of());
        }
    }

    /**
     * 初期配置のまま待機を抜けてRUNNINGにする
     */
    private static double settle(RLDribbleSession session, double now) {
        DribbleScenario s = session.getScenario().orElseThrow();
        World start = world(s.robotPos(), s.robotTheta(), s.ballPos());
        for (int i = 0; i < 200 && session.getPhase() != RLDribbleSession.Phase.RUNNING; i++) {
            now += CYCLE;
            step(session, start, now);
        }
        assertEquals(RLDribbleSession.Phase.RUNNING, session.getPhase());
        return now;
    }

    private static double begin(RLDribbleSession session, double now) {
        session.start();
        assertEquals(RLDribbleSession.Phase.RESETTING, session.getPhase(), session.getStatusText());
        assertEquals(RLDribbleSession.Directive.SETTLE, step(session, world(Vector2D.ZERO, 0.0, new Vector2D(3000, 0)), now));
        assertEquals(RLDribbleSession.Phase.SETTLING, session.getPhase());
        return settle(session, now);
    }

    /**
     * Moverを動かしてエピソードを最後まで進める
     *
     * @param aimOffset 目標方向からずらして蹴る角度
     * @return エピソードが終わった時刻
     */
    private static double play(RLDribbleSession session, double now, double aimOffset) {
        final DribbleScenario s = session.getScenario().orElseThrow();
        final int episodes = session.getCompletedEpisodes();
        Mover mover = new Mover(s);
        for (int i = 0; i < 2000 && session.getCompletedEpisodes() == episodes; i++) {
            now += CYCLE;
            final double aim = MathHelper.direction(s.kickTarget(), mover.robotPos) + aimOffset;
            final boolean kick = mover.move(aim);
            final RLDribbleSession.Directive d = step(session, mover.world(), now, kick ? 100.0 : 0.0);
            if (kick && d == RLDribbleSession.Directive.ACT)
                mover.kick();
        }
        assertEquals(episodes + 1, session.getCompletedEpisodes());
        return now;
    }

    /* ============================== 観測 ============================== */

    @Test
    void hiddenBallStaysAtLastSeenPosition() {
        // ロボットの横で影に入ったボールは、最後に見えた位置のまま (正面にあるとはみなさない)
        Vector2D robotPos = new Vector2D(500, -300);
        Vector2D side = robotPos.add(new Vector2D(0, 150));
        World w = world(robotPos, 0.0, Vector2D.ZERO, side, new Vector2D(300, 0), List.of());
        w.getBall().setLost(true);
        DribbleStepState s = DribbleStepState.measure(w, TeamColor.BLUE, 3, Vector2D.ZERO, false, Optional.empty(),
                config()).orElseThrow();
        float[] obs = DribbleObservation.build(s, new double[DribbleObservation.ACTION_DIM]);
        assertEquals(0.0f, obs[OBS.indexOf("ball_visible")]);
        assertEquals(0.0f, obs[OBS.indexOf("have_ball")]);
        assertEquals(0.0f, obs[OBS.indexOf("ball_rel_x")], 1e-4);
        assertEquals(0.15f, obs[OBS.indexOf("ball_rel_y")], 1e-4);
        // 見えないボールの速度は分からないので0 (相対速度はロボット速度の符号反転)
        assertEquals(0.0f, obs[OBS.indexOf("ball_vel_rel_x")], 1e-4);
    }

    @Test
    void observationIsInvariantToInversion() {
        Vector2D robotPos = new Vector2D(500, -300);
        double theta = 0.7;
        Vector2D ball = facePos(robotPos, theta);
        Vector2D target = new Vector2D(2500, 1000);
        List<Vector2D> opponents = List.of(new Vector2D(1500, 0), new Vector2D(800, -200));
        World w = world(robotPos, theta, new Vector2D(400, 100), ball, new Vector2D(400, 100), opponents);
        double[] prev = {0.1, 0.2, 0.3, 12, 0};
        Optional<Vector2D> start = Optional.of(new Vector2D(300, -500));

        DribbleStepState s = DribbleStepState.measure(w, TeamColor.BLUE, 3, target, true, start, config()).orElseThrow();
        DribbleStepState sInv = DribbleStepState.measure(w.getInverted(), TeamColor.BLUE, 3, target.negate(), true,
                start.map(Vector2D::negate), config()).orElseThrow();
        float[] obs = DribbleObservation.build(s, prev);
        float[] obsInv = DribbleObservation.build(sInv, prev);
        assertEquals(DribbleObservation.OBS_DIM, obs.length);
        assertEquals(5, DribbleObservation.ACTION_DIM);
        // 相手ゴールは反転すると反対側になるので除いて比較
        final int goal = OBS.indexOf("goal_rel_x");
        for (int i = 0; i < obs.length; i++) {
            if (i == goal || i == goal + 1) continue;
            assertEquals(obs[i], obsInv[i], 1e-4f, DribbleObservation.OBS_NAMES[i]);
        }
        assertEquals((float) contact() / 1000f, obs[OBS.indexOf("ball_rel_x")], 1e-4);
        assertEquals(1.0f, obs[OBS.indexOf("have_ball")]);
        assertEquals(1.0f, obs[OBS.indexOf("ball_visible")]);
        assertEquals(1.0f, obs[OBS.indexOf("is_shoot")]);
        // 目標への向き
        final double aim = MathHelper.direction(target, robotPos) - theta;
        assertEquals(FastMath.cos(aim), obs[OBS.indexOf("aim_cos")], 1e-5);
        assertEquals(FastMath.sin(aim), obs[OBS.indexOf("aim_sin")], 1e-5);
        assertEquals(1.0f, obs[OBS.indexOf("dribbling")]);
        assertEquals(MathHelper.distance2D(ball, start.get()) / 1000.0, obs[OBS.indexOf("dribble_dist")], 1e-4);
        // 相手は近い順
        assertEquals(1.0f, obs[OBS.indexOf("opp0_present")]);
        assertEquals(1.0f, obs[OBS.indexOf("opp1_present")]);
        assertEquals(0.0f, obs[OBS.indexOf("opp2_present")]);
        final double d0 = FastMath.hypot(obs[OBS.indexOf("opp0_rel_x")], obs[OBS.indexOf("opp0_rel_y")]);
        assertEquals(MathHelper.distance2D(robotPos, new Vector2D(800, -200)) / 1000.0, d0, 1e-4);
        // 相手ゴールはワールドの+x側
        assertEquals(MathHelper.distance2D(robotPos, new Vector2D(new Field().getMaxX(), 0)) / 1000.0,
                FastMath.hypot(obs[goal], obs[goal + 1]), 1e-4);
    }

    @Test
    void parkedOpponentsAreNotObserved() {
        Field field = new Field();
        List<Vector2D> opponents = List.of(DribbleScenario.getParkPosition(field, 0), DribbleScenario.getParkPosition(field, 40));
        World w = world(Vector2D.ZERO, 0.0, Vector2D.ZERO, new Vector2D(500, 0), Vector2D.ZERO, opponents);
        DribbleStepState s = DribbleStepState.measure(w, TeamColor.BLUE, 3, Vector2D.ZERO, false, Optional.empty(), config()).orElseThrow();
        assertTrue(s.opponents().isEmpty());
        assertFalse(s.haveBall());
        assertTrue(s.dribbleStart().isEmpty());
        assertTrue(DribbleStepState.measure(w, TeamColor.BLUE, 99, Vector2D.ZERO, false, Optional.empty(), config()).isEmpty());
    }

    /* ============================== 初期配置 ============================== */

    @Test
    void passAndShootScenarios() {
        Field field = new Field();
        final Config c = config();
        int shoots = 0;
        for (long seed = 1; seed < 400; seed++) {
            final double goalSign = seed % 2 == 0 ? 1.0 : -1.0;
            DribbleScenario a = DribbleScenario.generate(seed, field, c, goalSign).orElseThrow();
            assertEquals(a, DribbleScenario.generate(seed, field, c, goalSign).orElseThrow());
            assertEquals(c.rlDribble.ballStartDistance, MathHelper.distance2D(a.robotPos(), a.ballPos()), 1e-6);
            // ボールはロボットの正面
            assertEquals(a.robotTheta(), MathHelper.direction(a.ballPos(), a.robotPos()), 1e-6);
            for (Vector2D p : List.of(a.robotPos(), a.ballPos())) {
                assertTrue(FastMath.abs(p.getX()) <= field.getMaxX() - c.rlDribble.spawnMargin + 1e-6);
                assertTrue(FastMath.abs(p.getY()) <= field.getMaxY() - c.rlDribble.spawnMargin + 1e-6);
            }
            if (a.shoot()) {
                shoots++;
                // 相手ゴールの中を狙う
                assertEquals(goalSign * field.getMaxX(), a.kickTarget().getX(), 1e-6);
                assertTrue(FastMath.abs(a.kickTarget().getY()) <= c.rlDribble.shootAimWidth * field.getGoalWidth() / 2.0 + 1e-6);
                assertTrue(MathHelper.distance2D(a.ballPos(), new Vector2D(a.kickTarget().getX(), 0.0)) <= c.rlDribble.shootMaxDist + 1e-6);
                // ペナルティエリアの外から蹴る
                assertTrue(goalSign * a.ballPos().getX() < field.getPenaltyFrontX());
            } else {
                final double dist = MathHelper.distance2D(a.ballPos(), a.kickTarget());
                assertTrue(dist >= c.rlDribble.passMinDist - 1e-6 && dist <= c.rlDribble.passMaxDist + 1e-6);
                assertTrue(FastMath.abs(a.kickTarget().getX()) <= field.getMaxX() - c.rlDribble.spawnMargin + 1e-6);
                assertTrue(FastMath.abs(a.kickTarget().getY()) <= field.getMaxY() - c.rlDribble.spawnMargin + 1e-6);
            }
        }
        // shootRatio = 0.5
        assertTrue(shoots > 150 && shoots < 250, "shoots " + shoots);
    }

    @Test
    void teleportParksRobotsOutsideTouchLine() {
        Field field = new Field();
        DribbleScenario s = DribbleScenario.generate(5, field, config(), 1.0).orElseThrow();
        SslSimulationControl.SimulatorControl control = s.buildTeleport(TeamColor.BLUE, 3, field,
                List.of(new Pair<>(TeamColor.BLUE, 1), new Pair<>(TeamColor.YELLOW, 0)));
        assertEquals(s.ballPos().getX() / 1000.0, control.getTeleportBall().getX(), 1e-4);
        assertEquals(3, control.getTeleportRobotCount());
        assertEquals(3, control.getTeleportRobot(0).getId().getId());
        assertEquals(s.robotTheta(), control.getTeleportRobot(0).getOrientation(), 1e-5);
        for (int i = 1; i < 3; i++) {
            final double y = control.getTeleportRobot(i).getY() * 1000.0;
            assertTrue(FastMath.abs(y) > field.getMaxY(), "parked robot must be outside the field");
            assertTrue(FastMath.abs(y) < field.getMaxY() + field.getGameMargin(), "parked robot must be inside the boundary");
        }
        for (int i = 0; i < 22; i++)
            for (int j = i + 1; j < 22; j++)
                assertTrue(MathHelper.distance2D(DribbleScenario.getParkPosition(field, i),
                        DribbleScenario.getParkPosition(field, j)) >= 2 * config().robotRadius);
    }

    /* ============================== セッション ============================== */

    @Test
    void passSucceedsThenMisses(@TempDir Path tempDir) throws Exception {
        rl().outputDir = tempDir.toString();
        rl().targetEpisodes = 2;
        rl().seed = 42;
        rl().shootRatio = 0.0;
        rl().resetPause = 1.0;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 100.0);
        final DribbleScenario first = session.getScenario().orElseThrow();

        // 1エピソード目: 目標へ正確に蹴る
        now = play(session, now, 0.0);
        assertTrue(session.getStatusText().contains("success 100%"), session.getStatusText());

        // 終了後 resetPause の間は停止してテレポートしない
        World w = world(Vector2D.ZERO, 0.0, new Vector2D(500, 0));
        final double end = now;
        for (double t = CYCLE; t < 0.95; t += CYCLE)
            assertEquals(RLDribbleSession.Directive.HALT, step(session, w, end + t));
        assertTrue(session.getStatusText().startsWith("pausing"), session.getStatusText());
        assertEquals(RLDribbleSession.Directive.SETTLE, step(session, w, end + 1.05));
        now = settle(session, end + 1.05);

        // 2エピソード目: 0.3rad ずらして蹴ると外れる
        play(session, now, 0.3);
        assertEquals(RLDribbleSession.Phase.FINISHED, session.getPhase());
        assertTrue(session.getStatusText().contains("success 50%"), session.getStatusText());

        RLDataWriter writer = session.getWriter().orElseThrow();
        assertTrue(writer.awaitTermination(10));
        Path runDir = writer.getRunDir();
        JsonObject meta = JsonParser.parseString(Files.readString(runDir.resolve("meta.json"))).getAsJsonObject();
        assertEquals(RLDataWriter.FORMAT_VERSION, meta.get("format_version").getAsInt());
        // 教師のデータは teacher ディレクトリに保存する
        assertEquals("teacher", runDir.getParent().getFileName().toString());
        assertEquals("policy", RLDataWriter.getSourceDir(EnumRLActionSource.POLICY_NOISE));
        assertEquals(DribbleObservation.VERSION, meta.get("obs_version").getAsInt());
        assertEquals(RLDataWriter.COLUMNS, meta.get("columns").getAsJsonArray().size());

        List<String> csv = Files.readAllLines(runDir.resolve("episodes.csv"), StandardCharsets.UTF_8);
        assertEquals(3, csv.size());
        assertTrue(csv.get(0).endsWith("shoot,min_target_dist"));
        String[] ep0 = csv.get(1).split(",");
        String[] ep1 = csv.get(2).split(",");
        assertEquals("SUCCESS", ep0[3]);
        assertEquals("MISSED", ep1[3]);
        assertEquals("0", ep0[8]);
        assertTrue(Double.parseDouble(ep0[9]) < config().rlDribble.passSuccessRadius);
        assertTrue(Double.parseDouble(ep1[9]) > config().rlDribble.passSuccessRadius);
        // 外したときは目標から外れた距離に応じたペナルティ
        assertTrue(Double.parseDouble(ep1[5]) < Double.parseDouble(ep0[5]));

        final List<String> cols = Arrays.asList(RLDataWriter.getColumnNames());
        float[][] rows = readRows(runDir.resolve("ep_000000.bin"));
        final float[] last = rows[rows.length - 1];
        final float[] kickStep = rows[rows.length - 2];
        assertEquals(1f, rows[0][cols.indexOf("is_first")]);
        assertEquals(1f, last[cols.indexOf("is_last")]);
        assertEquals(1f, last[cols.indexOf("is_terminal")]);
        assertEquals(0f, rows[0][cols.indexOf("have_ball")]);
        assertEquals(0f, rows[0][cols.indexOf("kick")]);
        // 蹴る直前のステップにキック指令と成功報酬が入る
        assertTrue(kickStep[cols.indexOf("kick")] > 0f);
        assertTrue(kickStep[cols.indexOf("reward")] > config().rlDribble.successReward - 1.0);
        assertEquals(0f, rows[0][cols.indexOf("teacher")]);
        assertEquals(-1f, last[cols.indexOf("teacher")]);
        // 教師の行動 (DAggerのラベル) は実際の行動とは別に記録する
        assertEquals(1.0f, rows[0][cols.indexOf("vx")]);
        assertEquals(0.5f, rows[0][cols.indexOf("expert_vx")]);
        assertEquals(0f, last[cols.indexOf("expert_vx")]);
        assertEquals(first.kickTarget().getX() / 1000.0, rows[0][cols.indexOf("target_x")], 1e-4);
        assertEquals(0f, rows[0][cols.indexOf("is_shoot")]);
        assertTrue(last[cols.indexOf("dribble_dist")] < 1.0f);
    }

    @Test
    void shootSucceedsWhenBallEntersGoal() {
        rl().saveData = false;
        rl().shootRatio = 1.0;
        rl().seed = 7;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 10.0);
        assertTrue(session.isShoot());
        assertEquals(new Field().getMaxX(), session.getTarget(false).orElseThrow().getX(), 1e-6);
        play(session, now, 0.0);
        assertTrue(session.getStatusText().contains("success 100%"), session.getStatusText());
    }

    @Test
    void excessiveDribbleBeforeKick() {
        rl().saveData = false;
        rl().shootRatio = 0.0;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 10.0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        // 保持したまま蹴らずに直進する
        Mover mover = new Mover(s);
        while (!mover.held) {
            now += CYCLE;
            mover.move(s.robotTheta());
            step(session, mover.world(), now);
        }
        final Vector2D dir = MathHelper.getFromPolar(1.0, mover.theta);
        for (int i = 0; i < 200 && session.getCompletedEpisodes() == 0; i++) {
            now += CYCLE;
            mover.robotPos = mover.robotPos.add(dir.scalarMultiply(1000.0 * CYCLE));
            mover.ballPos = facePos(mover.robotPos, mover.theta);
            mover.robotVel = dir.scalarMultiply(1000.0);
            step(session, mover.world(), now);
        }
        assertEquals(1, session.getCompletedEpisodes());
        assertTrue(session.getStatusText().contains("success 0%"));
        // 止まったのは保持した地点からおよそ1m
        assertTrue(MathHelper.distance2D(s.ballPos(), mover.ballPos) < config().rlDribble.dribbleLimit + 100.0);
    }

    @Test
    void dribbleLimitResetsWhenBallLeaves() {
        rl().saveData = false;
        rl().shootRatio = 0.0;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 10.0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        Mover mover = new Mover(s);
        while (!mover.held) {
            now += CYCLE;
            mover.move(s.robotTheta());
            step(session, mover.world(), now);
        }
        final Vector2D dir = MathHelper.getFromPolar(1.0, mover.theta);
        // 0.7m 運ぶ → ボールを離す → また触れて 0.7m 運ぶ (合計1.4mでも触れ続けた距離は1m以下)
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 42; i++) {
                now += CYCLE;
                mover.robotPos = mover.robotPos.add(dir.scalarMultiply(1000.0 * CYCLE));
                mover.ballPos = facePos(mover.robotPos, mover.theta);
                mover.robotVel = dir.scalarMultiply(1000.0);
                step(session, mover.world(), now);
            }
            assertTrue(session.getDribbleStart(false).isPresent());
            // ボールが見えないときは離れたか分からないので開始地点を保つ
            World hidden = TestRLDribble.world(mover.robotPos, mover.theta, Vector2D.ZERO,
                    mover.robotPos.add(dir.scalarMultiply(300.0)), Vector2D.ZERO, List.of());
            hidden.getBall().setLost(true);
            now += CYCLE;
            step(session, hidden, now);
            assertTrue(session.getDribbleStart(false).isPresent());
            // ロボット半径 + ボール半径より離れたら開始地点はリセットされる
            mover.ballPos = mover.robotPos.add(dir.scalarMultiply(config().robotRadius + config().ballRadius + 50.0));
            mover.robotVel = Vector2D.ZERO;
            now += CYCLE;
            step(session, mover.world(), now);
            assertTrue(session.getDribbleStart(false).isEmpty());
            mover.robotPos = mover.ballPos.subtract(dir.scalarMultiply(contact()));
        }
        assertEquals(RLDribbleSession.Phase.RUNNING, session.getPhase(), session.getStatusText());
        assertEquals(0, session.getCompletedEpisodes());
    }

    @Test
    void ballLostBeforePossession() {
        rl().saveData = false;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 10.0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        Vector2D ball = s.ballPos();
        final Vector2D away = MathHelper.getFromPolar(3000.0 * CYCLE, s.robotTheta());
        for (int i = 0; i < 100 && session.getCompletedEpisodes() == 0; i++) {
            now += CYCLE;
            ball = ball.add(away);
            step(session, world(s.robotPos(), s.robotTheta(), ball), now);
        }
        assertEquals(1, session.getCompletedEpisodes());
        assertTrue(MathHelper.distance2D(s.robotPos(), ball) > config().rlDribble.lostDistance);
    }

    @Test
    void timeoutIsNotTerminal(@TempDir Path tempDir) throws Exception {
        rl().outputDir = tempDir.toString();
        rl().targetEpisodes = 1;
        rl().maxEpisodeTime = 0.5;
        RLDribbleSession session = RLDribbleSession.getInstance();
        double now = begin(session, 10.0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        World still = world(s.robotPos(), s.robotTheta(), s.ballPos());
        for (int i = 0; i < 100 && session.getPhase() != RLDribbleSession.Phase.FINISHED; i++) {
            now += CYCLE;
            step(session, still, now);
        }
        RLDataWriter writer = session.getWriter().orElseThrow();
        assertTrue(writer.awaitTermination(10));
        assertTrue(Files.readAllLines(writer.getRunDir().resolve("episodes.csv")).get(1).contains(",TIMEOUT,"));
        final List<String> cols = Arrays.asList(RLDataWriter.getColumnNames());
        float[][] rows = readRows(writer.getRunDir().resolve("ep_000000.bin"));
        assertEquals(1f, rows[rows.length - 1][cols.indexOf("is_last")]);
        assertEquals(0f, rows[rows.length - 1][cols.indexOf("is_terminal")]);
    }

    @Test
    void sessionDiscardsEpisodeAfterInterruption() {
        rl().saveData = false;
        RLDribbleSession session = RLDribbleSession.getInstance();
        World w = world(Vector2D.ZERO, 0.0, Vector2D.ZERO);
        session.start();
        step(session, w, 10.0);
        assertEquals(RLDribbleSession.Phase.SETTLING, session.getPhase());
        // 黄チームからの呼び出しは無視される
        DribbleStepState s = DribbleStepState.measure(w, TeamColor.BLUE, 3, Vector2D.ZERO, false, Optional.empty(), config()).orElseThrow();
        assertEquals(RLDribbleSession.Directive.HALT, session.onObservation(TeamColor.YELLOW, 0, w, false,
                DribbleObservation.build(s, new double[DribbleObservation.ACTION_DIM]), s, 10.01));
        // 1秒空く (Run停止など) とやり直し
        step(session, w, 11.0);
        assertEquals(RLDribbleSession.Phase.SETTLING, session.getPhase());
        session.stop();
        assertEquals(RLDribbleSession.Phase.IDLE, session.getPhase());
    }

    @Test
    void startValidatesSettings() {
        RLDribbleSession session = RLDribbleSession.getInstance();
        ConfigManager.getInstance().getEditableConfig().radioTypes = new int[]{EnumRadioType.KIKS.getId()};
        session.start();
        assertTrue(session.getStatusText().contains("SIMULATOR"), session.getStatusText());

        ConfigManager.getInstance().getEditableConfig().radioTypes = new int[]{EnumRadioType.SIMULATOR.getId()};
        rl().lostDistance = 400.0;
        session.start();
        assertTrue(session.getStatusText().contains("lostDistance"), session.getStatusText());

        rl().lostDistance = 1000.0;
        rl().passMinDist = 5000.0;
        session.start();
        assertTrue(session.getStatusText().contains("passMinDist"), session.getStatusText());
        assertEquals(RLDribbleSession.Phase.FINISHED, session.getPhase());
    }

    /* ============================== Action ============================== */

    @Test
    void actionSendsVelocityCommands() {
        rl().saveData = false;
        rl().settleTime = 0.0;
        RLDribbleSession session = RLDribbleSession.getInstance();
        session.start();
        ActionRLDribble action = new ActionRLDribble(3, TeamColor.BLUE);
        action.setWorld(world(Vector2D.ZERO, 0.0, Vector2D.ZERO));
        action.update(0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        action.setTarget(s.kickTarget());
        for (int i = 1; i < 5; i++) {
            action.setWorld(world(s.robotPos(), s.robotTheta(), s.ballPos()));
            Command command = action.update(i);
            if (session.getPhase() == RLDribbleSession.Phase.RUNNING) {
                // 教師の角度指令・位置指令は速度に直して送る
                assertTrue(command.getTargetTheta().isEmpty());
                assertTrue(command.getTargetPos().isEmpty());
                assertFalse(command.isHalt());
            }
        }
        assertEquals(RLDribbleSession.Phase.RUNNING, session.getPhase());
    }

    /* ============================== 方策 (ONNX) ============================== */

    /**
     * RL_test の export_onnx.py で書き出した小さな乱数の方策 (隠れ層8)
     * policy_test_expected.json はPyTorchで同じ観測を入れたときの出力
     */
    private static Path testPolicy() throws URISyntaxException {
        return Paths.get(TestRLDribble.class.getResource("/rl/policy_test.onnx").toURI());
    }

    @Test
    void policyMatchesPython() throws Exception {
        final Path expectedPath = testPolicy().resolveSibling("policy_test_expected.json");
        final JsonObject expected = JsonParser.parseString(Files.readString(expectedPath)).getAsJsonObject();
        final JsonArray obsJson = expected.getAsJsonArray("obs");
        final JsonArray actionJson = expected.getAsJsonArray("action");
        float[] obs = new float[obsJson.size()];
        for (int i = 0; i < obs.length; i++) obs[i] = obsJson.get(i).getAsFloat();
        try (DribblePolicy policy = DribblePolicy.load(testPolicy())) {
            final double[] action = policy.act(obs);
            assertEquals(DribbleObservation.ACTION_DIM, action.length);
            for (int i = 0; i < action.length; i++)
                assertEquals(actionJson.get(i).getAsDouble(), action[i], 1e-3 * FastMath.max(1.0, FastMath.abs(action[i])));
        }
    }

    @Test
    void policyRejectsOtherObservationVersion(@TempDir Path dir) throws Exception {
        final Path onnx = dir.resolve("policy.onnx");
        Files.copy(testPolicy(), onnx);
        // 情報ファイルが無い
        assertThrows(IOException.class, () -> DribblePolicy.load(onnx));
        // 観測バージョンが違う
        Files.writeString(dir.resolve("policy.json"), String.format("{\"obs_version\": %d, \"obs_dim\": %d}",
                DribbleObservation.VERSION - 1, DribbleObservation.OBS_DIM));
        IOException e = assertThrows(IOException.class, () -> DribblePolicy.load(onnx));
        assertTrue(e.getMessage().contains("obs_version"), e.getMessage());
        // kick をパワーで出す古い形式
        Files.writeString(dir.resolve("policy.json"), String.format("{\"obs_version\": %d, \"obs_dim\": %d}",
                DribbleObservation.VERSION, DribbleObservation.OBS_DIM));
        e = assertThrows(IOException.class, () -> DribblePolicy.load(onnx));
        assertTrue(e.getMessage().contains("re-export"), e.getMessage());
        // ファイルが無い
        assertThrows(IOException.class, () -> DribblePolicy.load(dir.resolve("none.onnx")));
    }

    @Test
    void startFailsWithoutPolicy() {
        rl().saveData = false;
        rl().actionSource = EnumRLActionSource.POLICY;
        rl().policyPath = "none.onnx";
        RLDribbleSession session = RLDribbleSession.getInstance();
        session.start();
        assertEquals(RLDribbleSession.Phase.FINISHED, session.getPhase());
        assertTrue(session.getStatusText().contains("policy not found"), session.getStatusText());

        rl().policyPath = "";
        session.start();
        assertTrue(session.getStatusText().contains("path is empty"), session.getStatusText());
    }

    @Test
    void actionUsesPolicy() throws URISyntaxException, IOException, OrtException {
        rl().saveData = false;
        rl().settleTime = 0.0;
        rl().actionSource = EnumRLActionSource.POLICY;
        rl().policyPath = testPolicy().toString();
        RLDribbleSession session = RLDribbleSession.getInstance();
        session.start();
        assertTrue(session.getPolicy().isPresent(), session.getStatusText());
        ActionRLDribble action = new ActionRLDribble(3, TeamColor.BLUE);
        action.setWorld(world(Vector2D.ZERO, 0.0, Vector2D.ZERO));
        action.update(0);
        final DribbleScenario s = session.getScenario().orElseThrow();
        action.setTarget(s.kickTarget());
        final World start = world(s.robotPos(), s.robotTheta(), s.ballPos());
        Command command = null;
        for (int i = 1; i < 5 && session.getPhase() != RLDribbleSession.Phase.RUNNING; i++) {
            action.setWorld(start);
            command = action.update(i);
        }
        assertEquals(RLDribbleSession.Phase.RUNNING, session.getPhase());
        // 最初の行動は直前の行動0の観測から決まる
        DribbleStepState state = DribbleStepState.measure(start, TeamColor.BLUE, 3, s.kickTarget(), s.shoot(),
                Optional.empty(), config()).orElseThrow();
        try (DribblePolicy policy = DribblePolicy.load(testPolicy())) {
            final double[] expected = policy.act(DribbleObservation.build(state, new double[DribbleObservation.ACTION_DIM]));
            assertNotNull(command);
            assertEquals(expected[2], command.getTargetOmega(), 1e-6);
            assertEquals(FastMath.round(expected[3]), command.getDribble());
        }
    }

    /* ============================== 描画 ============================== */

    @Test
    void scenarioIsDrawnOnlyWhileCollecting() {
        rl().saveData = false;
        rl().shootRatio = 0.0;
        DrawRLDribble draw = new DrawRLDribble();
        LinkedHashMap<String, IGraphicalComponent> map = new LinkedHashMap<>();
        draw.init(VisionArea.class, map);
        assertFalse(map.isEmpty());
        RLDribbleSession session = RLDribbleSession.getInstance();
        draw.update(VisionArea.class, map, config());
        assertTrue(map.values().stream().allMatch(c -> c instanceof NoneCafe));

        session.start();
        step(session, world(Vector2D.ZERO, 0.0, Vector2D.ZERO), 10.0);
        draw.update(VisionArea.class, map, config());
        assertTrue(map.get("rl_dribble_target") instanceof CircleCafe);
        assertTrue(map.get("rl_dribble_limit") instanceof CircleCafe);
        assertTrue(map.get("rl_dribble_path") instanceof LineCafe);

        rl().drawScenario = false;
        draw.update(VisionArea.class, map, config());
        assertTrue(map.values().stream().allMatch(c -> c instanceof NoneCafe));

        rl().drawScenario = true;
        session.stop();
        draw.update(VisionArea.class, map, config());
        assertTrue(map.values().stream().allMatch(c -> c instanceof NoneCafe));
    }

    private static float[][] readRows(Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, buffer.remaining() % (Float.BYTES * RLDataWriter.COLUMNS));
        final int n = buffer.remaining() / Float.BYTES / RLDataWriter.COLUMNS;
        float[][] rows = new float[n][RLDataWriter.COLUMNS];
        for (float[] row : rows)
            for (int j = 0; j < row.length; j++) row[j] = buffer.getFloat();
        return rows;
    }
}
