package app.sentinela.core.model

/** Regras puras que definem o resultado de uma sessão a partir do estado de cada fonte. */
object SessionOutcome {

    /**
     * - COMPLETE: todas as fontes pedidas gravaram e nenhuma foi interrompida;
     * - PARTIAL_SUCCESS: ao menos uma fonte gravou;
     * - FAILED: nenhuma fonte chegou a gravar.
     */
    fun finalStatus(sources: Map<SourceKind, SourceReport>): SessionStatus {
        val requested = sources.values.filter { it.state != SourceState.NOT_REQUESTED }
        if (requested.isEmpty()) return SessionStatus.FAILED
        val produced = requested.count { it.everActive }
        return when {
            produced == 0 -> SessionStatus.FAILED
            produced == requested.size && requested.all { it.interruptions == 0 && it.state == SourceState.STOPPED } ->
                SessionStatus.COMPLETE
            else -> SessionStatus.PARTIAL_SUCCESS
        }
    }

    /** Estado final de uma fonte quando a sessão é encerrada. */
    fun stoppedState(current: SourceReport): SourceReport = when (current.state) {
        SourceState.ACTIVE -> current.copy(state = SourceState.STOPPED)
        SourceState.STARTING -> current.copy(
            state = SourceState.FAILED,
            detail = current.detail ?: "Sessão encerrada antes de a fonte iniciar",
        )
        SourceState.AWAITING_CONSENT -> current.copy(
            state = SourceState.USER_DENIED,
            detail = current.detail ?: "Sessão encerrada antes do consentimento",
        )
        else -> current
    }

    /** Decide a vibração de confirmação depois que as fontes imediatas assentaram. */
    fun startFeedback(sources: Map<SourceKind, SourceReport>, immediate: Set<SourceKind>): StartFeedback {
        val reports = immediate.map { sources[it] ?: SourceReport(SourceState.NOT_REQUESTED) }
        val active = reports.count { it.state == SourceState.ACTIVE }
        return when {
            active == 0 -> StartFeedback.NOTHING_ACTIVE
            active == reports.size -> StartFeedback.ALL_ACTIVE
            else -> StartFeedback.PARTIAL
        }
    }

    enum class StartFeedback { ALL_ACTIVE, PARTIAL, NOTHING_ACTIVE }
}
