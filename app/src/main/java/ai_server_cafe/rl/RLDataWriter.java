package ai_server_cafe.rl;

import ai_server_cafe.config.Config;
import ai_server_cafe.config.EnumRLActionSource;
import ai_server_cafe.config.EnumRLEnvironment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 収集したエピソードを保存する
 * <pre>
 * outputDir/{sim|real}/teacher/yyyyMMdd-HHmmss/   教師 (ActionKick) が動かしたデータ
 * outputDir/{sim|real}/policy/yyyyMMdd-HHmmss/    学習した方策が動かしたデータ (DAgger用に教師の行動も記録する)
 * sim はシミュレータ、real は実機 (meta.json の environment にも記録する)
 *   meta.json      列の定義・観測バージョン・収集時の設定
 *   episodes.csv   エピソードごとの集計 (min_target_dist は蹴った後にボールが目標に最も近づいた距離 [mm], 蹴っていなければ -1)
 *                  蹴った後の様子 (蹴っていなければ空欄):
 *                    kick_offset    蹴る直前のボールの横ずれ (ロボット座標系のy, 左が+) [mm]
 *                    kick_max_speed 蹴った後のボールの最高速度 [mm/s]
 *                    ball_end_x/y   結果が決まったときのボール位置 (フィールド座標系) [mm]
 *                    eval_end       結果の決まり方 GOAL / NEAR_TARGET (成功), STOPPED / PASSED / OUT / TIME (外れ)
 *   ep_000000.bin  float32 リトルエンディアン, [steps, columns] の行優先配列
 * </pre>
 * 行の意味はRLDSと同じ: reward[t] は action[t] の結果として得た報酬。
 * is_last の行は最終観測のみで action と reward は 0。
 * ファイル書き込みは制御ループを止めないよう別スレッドで行う。
 */
public final class RLDataWriter {
    public static final String FORMAT = "kiks-rl-dribble";
    // 2: teacher_valid列をteacher列に置き換えた
    // 3: 観測バージョン2, kick_theta と dribble_dist 列を追加
    // 4: 観測バージョン3 (パス・シュート), 行動にkick, kick_theta列をis_shoot列に置き換え, episodes.csvにshootとmin_target_distを追加
    // 5: DAgger用に教師の行動 (expert_*) 列を追加, 教師と方策で保存先を分けた
    //    (後から episodes.csv の後ろに kick_offset 〜 eval_end 列を追加。前の列は変わらないので版は同じ)
    public static final int FORMAT_VERSION = 5;

    // obs と action の後ろに続く列
    public static final String[] EXTRA_NAMES = {
            "reward", "is_first", "is_last", "is_terminal",
            "teacher",          // 行動の出どころ 0:ActionKick 1:学習した方策 -1:なし(最終行)
            // 同じ観測で教師 (ActionKick) が出す行動 (ノイズなし)。方策が動かしていても記録する (DAgger のラベル)
            "expert_vx", "expert_vy", "expert_omega", "expert_dribble", "expert_kick",
            "ball_contact",     // シミュレータの接触フィードバック 1/0/-1(不明)
            "ball_visible",
            // 以下はフィールド座標系 (invertなし) [m, rad], 解析用で観測には含めない
            "robot_x", "robot_y", "robot_theta",
            "ball_x", "ball_y",
            "target_x", "target_y", // パス・シュートの目標地点
            "is_shoot",
            "dribble_dist",     // ドリブル開始地点からボールまでの距離 [m]
            "time",             // エピソード開始からの経過時間 [s]
    };
    public static final int COLUMNS = DribbleObservation.OBS_DIM + DribbleObservation.ACTION_DIM + EXTRA_NAMES.length;

    private static final Logger LOGGER = LogManager.getLogger("rl data writer");
    private final Path runDir;
    private final ExecutorService executor;

    private RLDataWriter(Path runDir) {
        this.runDir = runDir;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "rl-data-writer"));
    }

    @Nonnull
    public static RLDataWriter create(@Nonnull Config config, long seed, @Nonnull String captainColor) throws IOException {
        final String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        final Path runDir = Paths.get(config.rlDribble.outputDir, getEnvironmentDir(config.rlDribble.environment),
                getSourceDir(config.rlDribble.actionSource), name);
        Files.createDirectories(runDir);
        RLDataWriter writer = new RLDataWriter(runDir);
        writer.writeMeta(config, seed, captainColor);
        Files.writeString(runDir.resolve("episodes.csv"),
                "episode,file,steps,reason,success,return,duration,scenario_seed,shoot,min_target_dist,"
                        + "kick_offset,kick_max_speed,ball_end_x,ball_end_y,eval_end\n", StandardCharsets.UTF_8);
        LOGGER.info("rl data will be saved to {}", runDir.toAbsolutePath());
        return writer;
    }

    /**
     * 環境ごとの保存先のディレクトリ名
     */
    @Nonnull
    public static String getEnvironmentDir(@Nonnull EnumRLEnvironment environment) {
        return environment == EnumRLEnvironment.REAL ? "real" : "sim";
    }

    /**
     * 行動の出どころごとの保存先のディレクトリ名
     */
    @Nonnull
    public static String getSourceDir(@Nonnull EnumRLActionSource source) {
        return source.usesPolicy() ? "policy" : "teacher";
    }

    @Nonnull
    public static String[] getColumnNames() {
        String[] names = new String[COLUMNS];
        int i = 0;
        for (String s : DribbleObservation.OBS_NAMES) names[i++] = s;
        for (String s : DribbleObservation.ACTION_NAMES) names[i++] = s;
        for (String s : EXTRA_NAMES) names[i++] = s;
        return names;
    }

    private void writeMeta(Config config, long seed, String captainColor) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject meta = new JsonObject();
        meta.addProperty("format", FORMAT);
        meta.addProperty("format_version", FORMAT_VERSION);
        meta.addProperty("obs_version", DribbleObservation.VERSION);
        meta.addProperty("obs_dim", DribbleObservation.OBS_DIM);
        meta.addProperty("action_dim", DribbleObservation.ACTION_DIM);
        meta.addProperty("dtype", "float32");
        meta.addProperty("byte_order", "little");
        JsonArray columns = new JsonArray();
        for (String s : getColumnNames()) columns.add(s);
        meta.add("columns", columns);
        meta.addProperty("cycle_time", config.getCycleTime());
        meta.addProperty("created", LocalDateTime.now().toString());
        meta.addProperty("seed", seed);
        meta.addProperty("captain_color", captainColor);
        meta.addProperty("environment", config.rlDribble.environment.name());
        meta.addProperty("reset_mode", config.rlDribble.resetMode.name());
        JsonObject configs = new JsonObject();
        configs.add("rlDribble", gson.toJsonTree(config.rlDribble));
        configs.add("dribbleState", gson.toJsonTree(config.dribbleState));
        configs.add("controllerConfig", gson.toJsonTree(config.controllerConfig));
        configs.addProperty("robotRadius", config.robotRadius);
        configs.addProperty("toFaceRadius", config.toFaceRadius);
        configs.addProperty("ballRadius", config.ballRadius);
        meta.add("config", configs);
        try (Writer writer = Files.newBufferedWriter(this.runDir.resolve("meta.json"), StandardCharsets.UTF_8)) {
            gson.toJson(meta, writer);
        }
    }

    /**
     * 蹴った後のボールの様子 (外れた原因を調べるため episodes.csv に残す)
     *
     * @param offset   蹴る直前のボールの横ずれ [mm]
     * @param maxSpeed 蹴った後のボールの最高速度 [mm/s]
     * @param endPos   結果が決まったときのボール位置 (フィールド座標系)
     * @param end      結果の決まり方
     */
    public record KickResult(double offset, double maxSpeed, Optional<Vector2D> endPos, String end) {
        public static final KickResult NONE = new KickResult(Double.NaN, Double.NaN, Optional.empty(), "");

        String toCsv() {
            if (Double.isNaN(this.offset))
                return ",,,,";
            return String.format(Locale.ROOT, "%.1f,%.0f,%s,%s,%s", this.offset, this.maxSpeed,
                    this.endPos.map(p -> String.format(Locale.ROOT, "%.0f", p.getX())).orElse(""),
                    this.endPos.map(p -> String.format(Locale.ROOT, "%.0f", p.getY())).orElse(""), this.end);
        }
    }

    /**
     * エピソードを非同期で保存する
     */
    public void writeEpisode(int index, @Nonnull List<float[]> rows, @Nonnull String reason, boolean success,
                             double episodeReturn, double duration, long scenarioSeed, boolean shoot,
                             double minTargetDistance, @Nonnull KickResult kick) {
        final List<float[]> copied = List.copyOf(rows);
        this.executor.submit(() -> {
            final String fileName = String.format("ep_%06d.bin", index);
            ByteBuffer buffer = ByteBuffer.allocate(copied.size() * COLUMNS * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] row : copied) {
                for (float v : row) buffer.putFloat(v);
            }
            try (OutputStream out = Files.newOutputStream(this.runDir.resolve(fileName))) {
                out.write(buffer.array());
                Files.writeString(this.runDir.resolve("episodes.csv"),
                        String.format(Locale.ROOT, "%d,%s,%d,%s,%d,%.6f,%.4f,%d,%d,%.1f,%s%n", index, fileName, copied.size(),
                                reason, success ? 1 : 0, episodeReturn, duration, scenarioSeed, shoot ? 1 : 0,
                                minTargetDistance, kick.toCsv()),
                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.error("failed to write episode {} : {}", index, e.getMessage());
            }
        });
    }

    /**
     * 新しい書き込みを受け付けなくする (受付済みのエピソードは書き込まれる)
     * 制御ループから呼ばれるので完了は待たない
     */
    public void close() {
        this.executor.shutdown();
    }

    /**
     * 受付済みのエピソードの書き込み完了を待つ (テスト用)
     */
    public boolean awaitTermination(long seconds) throws InterruptedException {
        return this.executor.awaitTermination(seconds, TimeUnit.SECONDS);
    }

    @Nonnull
    public Path getRunDir() {
        return this.runDir;
    }
}
