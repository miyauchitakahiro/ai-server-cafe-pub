package ai_server_cafe.config;

/**
 * パス・シュートの強化学習で実際にロボットへ送る行動の出どころ
 */
public enum EnumRLActionSource {
    // 既存のActionKickをそのまま使う (行動模倣用の教師データ)
    TEACHER,
    // ActionKickの出力に相関付きガウスノイズを足す (探索付きデータ)
    TEACHER_NOISE,
    // 学習した方策 (rlDribble.policyPath のONNX) を使う (評価用)
    POLICY,
    // 方策の出力にノイズを足す (方策で探索しながらデータを集める)
    POLICY_NOISE;

    public boolean usesPolicy() {
        return this == POLICY || this == POLICY_NOISE;
    }

    public boolean addsNoise() {
        return this == TEACHER_NOISE || this == POLICY_NOISE;
    }
}
