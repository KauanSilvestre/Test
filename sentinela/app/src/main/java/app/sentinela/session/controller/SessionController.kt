package app.sentinela.session.controller

import app.sentinela.capture.CaptureEngine
import app.sentinela.core.model.CaptureSupport
import app.sentinela.core.model.DeviceInfo
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.SessionIds
import app.sentinela.core.model.SessionManifest
import app.sentinela.core.model.SessionOutcome
import app.sentinela.core.model.SessionStatus
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceReport
import app.sentinela.core.model.SourceState
import app.sentinela.core.storage.EventJournal
import app.sentinela.core.storage.JournalEvents
import app.sentinela.core.storage.SessionStore
import app.sentinela.core.storage.StoragePolicy
import java.time.Instant

/**
 * Orquestra uma sessão. Princípio central: **uma sessão não é binária**. Cada
 * fonte é iniciada de forma independente; se uma falha, as outras continuam. O
 * manifesto registra exatamente o que foi pedido, o que iniciou e o que falhou.
 *
 * Fluxo de [start]:
 * 1. cria o `session_id` e a pasta;
 * 2. grava `SESSION_START_REQUESTED` no journal;
 * 3. monta o manifesto inicial marcando cada fonte (STARTING / AWAITING_CONSENT
 *    / UNSUPPORTED / USER_DENIED) a partir do suporte do aparelho e do consentimento;
 * 4. inicia cada engine e coleta o resultado real;
 * 5. persiste o estado efetivo e grava `SESSION_ACTIVE`;
 * 6. só então devolve o resultado, que decide a confirmação háptica.
 */
class SessionController(
    private val store: SessionStore,
    private val factory: CaptureEngineFactory,
    private val device: DeviceInfo,
    private val support: CaptureSupport,
    private val clock: () -> Instant = Instant::now,
) {
    private val engines = linkedMapOf<SourceKind, CaptureEngine>()
    private var ledger: SessionLedger? = null

    val activeLedger: SessionLedger? get() = ledger

    data class StartResult(
        val manifest: SessionManifest,
        val feedback: SessionOutcome.StartFeedback,
        val startedStorageShort: Boolean,
    )

    fun start(profile: RecordingProfile, ctx: CaptureContext): StartResult {
        check(ledger == null) { "Já existe uma sessão ativa" }

        val id = SessionIds.newId(clock())
        store.createSessionDir(id)
        val journal = EventJournal(store.journalFile(id), clock)
        journal.append(JournalEvents.SESSION_START_REQUESTED, detail = "profile=${profile.kind}")

        val requested = profile.requestedSources()
        val initialSources = buildInitialSources(requested, ctx)
        val manifest = SessionManifest(
            sessionId = id,
            profile = profile,
            startedAt = clock().toString(),
            status = SessionStatus.STARTING,
            sources = initialSources,
            device = device,
        )
        val ledger = SessionLedger(store, manifest, journal, clock)
        this.ledger = ledger

        val storageShort = !StoragePolicy.canStart(store.usableBytes())
        if (storageShort) {
            journal.append(JournalEvents.LOW_STORAGE, detail = "livre=${store.usableBytes()} bytes ao iniciar")
        }

        // Inicia cada fonte que está em STARTING. Fontes já marcadas como
        // UNSUPPORTED/USER_DENIED não são tocadas.
        requested.forEach { source ->
            if (ledger.source(source).state != SourceState.STARTING) return@forEach
            if (storageShort && source.isVideo) {
                ledger.setSource(source, SourceState.FAILED, "Espaço insuficiente para vídeo")
                return@forEach
            }
            startSource(source, profile, ledger, ctx)
        }

        ledger.setStatus(SessionStatus.ACTIVE)
        ledger.event(JournalEvents.SESSION_ACTIVE)

        val immediate = requested.filterNot { it.needsProjection && ctx.projection == null }.toSet()
        val feedback = SessionOutcome.startFeedback(ledger.manifest.value.sources, immediate)
        return StartResult(ledger.manifest.value, feedback, storageShort)
    }

    private fun buildInitialSources(
        requested: Set<SourceKind>,
        ctx: CaptureContext,
    ): Map<SourceKind, SourceReport> {
        val all = SourceKind.entries.associateWith { SourceReport(SourceState.NOT_REQUESTED) }.toMutableMap()
        requested.forEach { source ->
            all[source] = when {
                !support.supports(source) -> SourceReport(SourceState.UNSUPPORTED, "Não suportado neste aparelho")
                source == SourceKind.SCREEN && ctx.projection == null ->
                    SourceReport(SourceState.USER_DENIED, "Sem consentimento de captura de tela")
                source == SourceKind.PLAYBACK_AUDIO && ctx.projection == null ->
                    SourceReport(SourceState.USER_DENIED, "Sem consentimento de captura de áudio interno")
                else -> SourceReport(SourceState.STARTING)
            }
        }
        return all
    }

    private fun startSource(
        source: SourceKind,
        profile: RecordingProfile,
        ledger: SessionLedger,
        ctx: CaptureContext,
    ) {
        val engine = factory.create(source, profile, ledger, ctx)
        if (engine == null) {
            ledger.setSource(source, SourceState.UNSUPPORTED, "Fonte não disponível nesta versão")
            return
        }
        when (val result = engine.start()) {
            CaptureEngine.StartResult.Active -> {
                engines[source] = engine
                // Câmera/tela confirmam ACTIVE pelo próprio callback; o áudio
                // confirma aqui, pois não há evento assíncrono de início.
                if (source == SourceKind.MICROPHONE) ledger.setSource(source, SourceState.ACTIVE)
            }
            CaptureEngine.StartResult.Unsupported ->
                ledger.setSource(source, SourceState.UNSUPPORTED, "Não suportado neste aparelho")
            CaptureEngine.StartResult.PermissionDenied ->
                ledger.setSource(source, SourceState.PERMISSION_DENIED, "Permissão não concedida")
            is CaptureEngine.StartResult.Failed ->
                ledger.setSource(source, SourceState.FAILED, result.reason)
        }
    }

    /** Força a parada de vídeo quando o disco aperta, mantendo o áudio. */
    fun stopVideoSources() {
        val ledger = ledger ?: return
        engines.filterKeys { it.isVideo }.forEach { (source, engine) ->
            engine.stop()
            engines.remove(source)
            if (ledger.source(source).state == SourceState.ACTIVE) {
                ledger.setSource(source, SourceState.STOPPED, "Interrompido por falta de espaço")
            }
        }
    }

    fun stop(): SessionManifest? {
        val ledger = ledger ?: return null
        ledger.setStatus(SessionStatus.STOPPING)
        ledger.event(JournalEvents.STOP_REQUESTED)

        engines.values.forEach { runCatching { it.stop() } }
        engines.clear()

        ledger.awaitFinalization()
        val final = ledger.finish(clock())
        this.ledger = null
        return final
    }
}
