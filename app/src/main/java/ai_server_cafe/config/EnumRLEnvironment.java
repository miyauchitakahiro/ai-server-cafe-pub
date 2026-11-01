package ai_server_cafe.config;

/**
 * パス・シュートの強化学習を動かす環境
 */
public enum EnumRLEnvironment {
    // シミュレータ (ER-Force sim, radioTypesに3が必要)
    SIMULATOR,
    // 実機 (KIKSの無線, radioTypesに0が必要)。テレポートできない
    REAL
}
