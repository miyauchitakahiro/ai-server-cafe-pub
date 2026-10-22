package ai_server_cafe.rl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 学習した方策 (ONNX) で行動を決める
 * <p>
 * ONNXはRL_testの export_onnx.py で作る。
 * 入力 "obs" は DribbleObservation#build の出力そのまま、出力 "action" は DribbleObservation#ACTION_NAMES の順で
 * vx, vy, omega, dribble は行動と同じ単位 (観測の正規化と単位への変換はモデルの中で行う)。
 * kick だけはパワーではなく蹴るかどうかの判断 (1: 蹴る, 0: 蹴らない)。
 * 同じ名前の .json に書かれた観測バージョンが DribbleObservation#VERSION と違うときは読み込まない。
 */
public final class DribblePolicy implements AutoCloseable {
    private final OrtEnvironment env;
    private final OrtSession session;
    private final Path path;

    private DribblePolicy(OrtEnvironment env, OrtSession session, Path path) {
        this.env = env;
        this.session = session;
        this.path = path;
    }

    /**
     * 方策を読み込む
     *
     * @throws IOException ファイルが無い・観測の定義が合わないなど
     */
    @Nonnull
    public static DribblePolicy load(@Nonnull Path onnxPath) throws IOException {
        if (!Files.isRegularFile(onnxPath))
            throw new IOException("policy not found: " + onnxPath.toAbsolutePath());
        checkInfo(onnxPath);
        try {
            final OrtEnvironment env = OrtEnvironment.getEnvironment();
            // 制御ループを邪魔しないよう1スレッドで推論する (小さなネットワークなので十分速い)
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                return new DribblePolicy(env, env.createSession(onnxPath.toString(), options), onnxPath);
            }
        } catch (OrtException e) {
            throw new IOException("failed to load policy: " + e.getMessage(), e);
        }
    }

    /**
     * export_onnx.py が書く policy.json を見て、観測の定義が今のJavaと同じか確かめる
     */
    private static void checkInfo(Path onnxPath) throws IOException {
        final String name = onnxPath.getFileName().toString().replaceFirst("\\.onnx$", "") + ".json";
        final Path infoPath = onnxPath.resolveSibling(name);
        if (!Files.isRegularFile(infoPath))
            throw new IOException("policy info not found: " + infoPath.toAbsolutePath());
        final JsonObject info = JsonParser.parseString(Files.readString(infoPath, StandardCharsets.UTF_8)).getAsJsonObject();
        final int obsVersion = info.get("obs_version").getAsInt();
        final int obsDim = info.get("obs_dim").getAsInt();
        if (obsVersion != DribbleObservation.VERSION || obsDim != DribbleObservation.OBS_DIM)
            throw new IOException(String.format("policy is for obs_version %d (dim %d), but current is %d (dim %d)",
                    obsVersion, obsDim, DribbleObservation.VERSION, DribbleObservation.OBS_DIM));
        // kick を蹴るかどうかの判断として出す形式か (古い export_onnx.py はパワーを出していた)
        if (!info.has("kick_output") || !"decision".equals(info.get("kick_output").getAsString()))
            throw new IOException("policy outputs kick power (old format), re-export it with export_onnx.py");
    }

    /**
     * 観測から行動を決める
     *
     * @param obs DribbleObservation#build の出力
     * @return DribbleObservation#ACTION_NAMES の順の行動 (kickは 1: 蹴る, 0: 蹴らない)
     */
    @Nonnull
    public double[] act(@Nonnull float[] obs) throws OrtException {
        final long[] shape = {1, obs.length};
        try (OnnxTensor input = OnnxTensor.createTensor(this.env, FloatBuffer.wrap(obs), shape);
             OrtSession.Result result = this.session.run(Map.of("obs", input))) {
            final float[] out = ((float[][]) result.get(0).getValue())[0];
            final double[] action = new double[out.length];
            for (int i = 0; i < out.length; i++)
                action[i] = out[i];
            return action;
        }
    }

    @Nonnull
    public Path getPath() {
        return this.path;
    }

    @Override
    public void close() {
        try {
            this.session.close();
        } catch (OrtException ignored) {
            // 閉じるときの失敗は無視する
        }
    }
}
