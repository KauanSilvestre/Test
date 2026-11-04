package app.sentinela.capture

import app.sentinela.core.model.SourceKind

/**
 * Contrato comum a toda fonte de captura. Cada engine é independente: inicia,
 * grava os próprios segmentos e para sozinho. A falha de um nunca derruba os
 * demais — o [app.sentinela.session.controller.SessionLedger] registra o estado
 * real de cada um.
 */
interface CaptureEngine {
    val source: SourceKind

    /**
     * Tenta iniciar a captura. Retorna o resultado real; nunca lança para
     * sinalizar indisponibilidade esperada (sem permissão, hardware ausente).
     */
    fun start(): StartResult

    /** Encerra a captura e finaliza o segmento aberto. Idempotente. */
    fun stop()

    sealed interface StartResult {
        data object Active : StartResult
        data object Unsupported : StartResult
        data object PermissionDenied : StartResult
        data class Failed(val reason: String) : StartResult
    }
}
