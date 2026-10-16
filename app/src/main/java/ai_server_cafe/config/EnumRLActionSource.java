package ai_server_cafe.config;

/**
 * ドリブル強化学習で実際にロボットへ送る行動の出どころ
 */
public enum EnumRLActionSource {
    // 既存のActionDribbleをそのまま使う (行動模倣用の教師データ)
    TEACHER,
    // ActionDribbleの出力に相関付きガウスノイズを足す (探索付きデータ)
    TEACHER_NOISE
}
