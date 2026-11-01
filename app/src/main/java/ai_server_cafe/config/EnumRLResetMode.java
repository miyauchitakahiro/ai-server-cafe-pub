package ai_server_cafe.config;

/**
 * パス・シュートの強化学習でエピソードの初期配置を作る方法
 */
public enum EnumRLResetMode {
    // ロボットとボールをシミュレータでテレポートする (シミュレータのみ)
    TELEPORT,
    // ボールが止まっている位置を基準に初期配置を決め、ロボットが自走して開始位置へ行く (実機でも使える)
    FROM_BALL
}
