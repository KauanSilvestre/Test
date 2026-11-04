package app.sentinela.session.controller

import app.sentinela.core.hashing.Sha256
import app.sentinela.core.model.SegmentInfo
import app.sentinela.core.model.SegmentStatus
import app.sentinela.core.model.SessionManifest
import app.sentinela.core.model.SessionOutcome
import app.sentinela.core.model.SessionStatus
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceReport
import app.sentinela.core.model.SourceState
import app.sentinela.core.storage.EventJournal
import app.sentinela.core.storage.JournalEvents
import app.sentinela.core.storage.Mp4Inspector
import app.sentinela.core.storage.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Um segmento aberto por um capturador. */
data class SegmentHandle(val source: SourceKind, val index: Int, val file: File) {
    val name: String get() = file.name
}

/**
 * Único ponto que altera o estado de uma sessão. Os capturadores rodam em
 * threads próprias e chamam o ledger; ele serializa as mudanças, grava o
 * journal, persiste o manifesto de forma atômica e calcula o SHA-256 de cada
 * segmento assim que ele é fechado.
 */
class SessionLedger(
    private val store: SessionStore,
    initial: SessionManifest,
    private val journal: EventJournal,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private val nextIndex = mutableMapOf<SourceKind, Int>()
    private val hasher: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "sentinela-hash") }
    private val _manifest = MutableStateFlow(initial)

    val manifest: StateFlow<SessionManifest> = _manifest.asStateFlow()
    val sessionId: String = initial.sessionId
    val directory: File = store.sessionDir(initial.sessionId)

    init {
        synchronized(lock) { persist(initial) }
    }

    fun event(type: String, source: SourceKind? = null, detail: String? = null) = synchronized(lock) {
        journal.append(type, source, detail)
        persist(_manifest.value)
    }

    fun source(kind: SourceKind): SourceReport = _manifest.value.source(kind)

    fun setSource(kind: SourceKind, state: SourceState, detail: String? = null) = synchronized(lock) {
        val current = _manifest.value.source(kind)
        if (current.state == state && current.detail == detail) return@synchronized
        val interrupted = current.state == SourceState.ACTIVE &&
            (state == SourceState.INTERRUPTED || state == SourceState.FAILED)
        val updated = current.copy(
            state = state,
            detail = detail,
            everActive = current.everActive || state == SourceState.ACTIVE,
            interruptions = current.interruptions + if (interrupted) 1 else 0,
        )
        journal.append(JournalEvents.SOURCE_STATE, kind, if (detail == null) state.name else "${state.name}: $detail")
        persist(_manifest.value.let { it.copy(sources = it.sources + (kind to updated)) })
    }

    fun setStatus(status: SessionStatus) = synchronized(lock) {
        if (_manifest.value.status == status) return@synchronized
        persist(_manifest.value.copy(status = status))
    }

    /** Reserva o próximo arquivo de segmento da fonte e o registra como RECORDING. */
    fun openSegment(source: SourceKind): SegmentHandle = synchronized(lock) {
        val index = nextIndex[source] ?: 1
        nextIndex[source] = index + 1
        val name = String.format(Locale.ROOT, "%s_%04d.%s", source.filePrefix, index, source.fileExtension)
        val segment = SegmentInfo(source = source, index = index, file = name, startedAt = clock().toString())
        journal.append(JournalEvents.SEGMENT_OPENED, source, name)
        persist(_manifest.value.let { it.copy(segments = it.segments + segment) })
        SegmentHandle(source, index, File(directory, name))
    }

    /**
     * Marca o segmento como fechado e agenda o hash numa thread dedicada, para
     * não atrasar o capturador. Segmentos vazios são descartados e registrados.
     */
    fun closeSegment(handle: SegmentHandle) {
        val endedAt = clock().toString()
        hasher.execute { finalizeSegment(handle, endedAt) }
    }

    private fun finalizeSegment(handle: SegmentHandle, endedAt: String) {
        val file = handle.file
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            synchronized(lock) {
                journal.append(JournalEvents.SEGMENT_DISCARDED_EMPTY, handle.source, handle.name)
                persist(_manifest.value.let { m -> m.copy(segments = m.segments.filterNot { it.file == handle.name }) })
            }
            return
        }
        val hash = Sha256.ofFile(file)
        val bytes = file.length()
        val status = if (handle.source.isVideo && !Mp4Inspector.hasMoov(file)) {
            SegmentStatus.POSSIBLY_CORRUPT
        } else {
            SegmentStatus.FINALIZED
        }
        synchronized(lock) {
            journal.append(
                JournalEvents.SEGMENT_FINALIZED,
                handle.source,
                "${handle.name} sha256=$hash bytes=$bytes status=$status",
            )
            persist(
                _manifest.value.let { m ->
                    m.copy(
                        segments = m.segments.map {
                            if (it.file == handle.name) {
                                it.copy(endedAt = endedAt, bytes = bytes, sha256 = hash, status = status)
                            } else {
                                it
                            }
                        },
                    )
                },
            )
        }
    }

    /** Aguarda o hash de todos os segmentos já fechados. Chamado uma única vez, no encerramento. */
    fun awaitFinalization(timeoutMillis: Long = 60_000): Boolean {
        hasher.shutdown()
        return hasher.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    /** Fecha a sessão: assenta o estado de cada fonte, calcula o resultado e grava o evento final. */
    fun finish(endedAt: Instant = clock()): SessionManifest = synchronized(lock) {
        val m = _manifest.value
        val settled = m.sources.mapValues { (_, report) -> SessionOutcome.stoppedState(report) }
        settled.forEach { (kind, report) ->
            if (report != m.source(kind)) {
                journal.append(JournalEvents.SOURCE_STATE, kind, "${report.state.name}: ${report.detail.orEmpty()}".trimEnd(' ', ':'))
            }
        }
        val status = SessionOutcome.finalStatus(settled)
        journal.append(
            JournalEvents.SESSION_FINALIZED,
            detail = "status=$status segments=${m.segments.size} bytes=${m.totalBytes}",
        )
        val final = m.copy(sources = settled, status = status, endedAt = endedAt.toString())
        persist(final)
        final
    }

    private fun persist(manifest: SessionManifest) {
        val withHead = manifest.copy(journalHead = journal.head)
        store.writeManifest(withHead)
        _manifest.value = withHead
    }
}
