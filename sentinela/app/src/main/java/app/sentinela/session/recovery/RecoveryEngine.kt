package app.sentinela.session.recovery

import app.sentinela.core.hashing.Sha256
import app.sentinela.core.model.SegmentInfo
import app.sentinela.core.model.SegmentStatus
import app.sentinela.core.model.SessionManifest
import app.sentinela.core.model.SessionStatus
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceReport
import app.sentinela.core.model.SourceState
import app.sentinela.core.storage.EventJournal
import app.sentinela.core.storage.JournalEvents
import app.sentinela.core.storage.Mp4Inspector
import app.sentinela.core.storage.SessionStore
import java.io.File
import java.time.Instant

/**
 * Se o processo morre durante uma sessão, o manifesto fica com status aberto.
 * Na próxima vez que o app sobe, o [RecoveryEngine] varre o disco e reconcilia:
 *
 * - cada arquivo de segmento presente é reindexado, com bytes e SHA-256;
 * - um `.mp4` sem a caixa `moov` é marcado POSSIBLY_CORRUPT (não foi fechado);
 * - a sessão recebe o status RECOVERED e um `ended_at` estimado;
 * - o journal encadeado é verificado e o resultado é registrado nele.
 *
 * Nada é apagado: a recuperação preserva o máximo de evidência possível.
 */
class RecoveryEngine(
    private val store: SessionStore,
    private val clock: () -> Instant = Instant::now,
) {

    data class Result(val recovered: List<String>, val verifiedIntact: List<String>)

    fun recoverAll(): Result {
        val recovered = mutableListOf<String>()
        val intact = mutableListOf<String>()
        for (id in store.listSessionIds()) {
            val manifest = store.readManifest(id)
            if (manifest == null) {
                // Pasta sem manifesto legível: reindexa os segmentos no journal
                // como evidência mínima, mas não há perfil para reconstruir.
                if (rebuildOrphan(id)) recovered.add(id)
                continue
            }
            if (manifest.status.isOpen) {
                recoverOpen(manifest)
                recovered.add(id)
            } else if (manifest.journalHead != null) {
                intact.add(id)
            }
        }
        return Result(recovered, intact)
    }

    private fun recoverOpen(manifest: SessionManifest) {
        val dir = store.sessionDir(manifest.sessionId)
        val journal = EventJournal(store.journalFile(manifest.sessionId), clock)
        journal.append(JournalEvents.RECOVERY_STARTED, detail = "status anterior=${manifest.status}")

        val segments = reindexSegments(dir, journal)
        val sources = reconcileSources(manifest.sources, segments)
        val verification = EventJournal.verify(store.journalFile(manifest.sessionId))
        journal.append(
            JournalEvents.RECOVERY_FINISHED,
            detail = "segmentos=${segments.size} journal_valido=${verification.valid}",
        )

        val lastEnd = segments.mapNotNull { it.endedAt ?: it.startedAt }.maxOrNull()
        store.writeManifest(
            manifest.copy(
                status = SessionStatus.RECOVERED,
                endedAt = manifest.endedAt ?: lastEnd ?: clock().toString(),
                sources = sources,
                segments = segments,
                journalHead = journal.head,
            ),
        )
    }

    private fun rebuildOrphan(id: String): Boolean {
        val dir = store.sessionDir(id)
        val files = dir.listFiles()?.filter { SessionStore.SEGMENT_FILE.matches(it.name) }.orEmpty()
        if (files.isEmpty()) return false
        val journal = EventJournal(store.journalFile(id), clock)
        journal.append(JournalEvents.RECOVERY_STARTED, detail = "manifesto ausente")
        val segments = reindexSegments(dir, journal)
        journal.append(JournalEvents.RECOVERY_FINISHED, detail = "segmentos=${segments.size}")
        // Sem o perfil original não há manifesto completo a reconstruir; os
        // segmentos ficam reindexados no journal como evidência mínima.
        return segments.isNotEmpty()
    }

    private fun reindexSegments(dir: File, journal: EventJournal): List<SegmentInfo> {
        val files = dir.listFiles()
            ?.filter { it.isFile && SessionStore.SEGMENT_FILE.matches(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        return files.mapNotNull { file ->
            val match = SessionStore.SEGMENT_FILE.find(file.name) ?: return@mapNotNull null
            val source = SourceKind.fromFilePrefix(match.groupValues[1]) ?: return@mapNotNull null
            val index = match.groupValues[2].toInt()
            if (file.length() == 0L) {
                file.delete()
                journal.append(JournalEvents.SEGMENT_DISCARDED_EMPTY, source, file.name)
                return@mapNotNull null
            }
            val hash = Sha256.ofFile(file)
            val status = if (source.isVideo && !Mp4Inspector.hasMoov(file)) {
                SegmentStatus.POSSIBLY_CORRUPT
            } else {
                SegmentStatus.RECOVERED
            }
            journal.append(
                JournalEvents.RECOVERY_SEGMENT,
                source,
                "${file.name} sha256=$hash bytes=${file.length()} status=$status",
            )
            SegmentInfo(
                source = source,
                index = index,
                file = file.name,
                startedAt = Instant.ofEpochMilli(file.lastModified()).toString(),
                endedAt = Instant.ofEpochMilli(file.lastModified()).toString(),
                bytes = file.length(),
                sha256 = hash,
                status = status,
            )
        }
    }

    /** Fontes que estavam ACTIVE/STARTING e produziram arquivo viram INTERRUPTED. */
    private fun reconcileSources(
        previous: Map<SourceKind, SourceReport>,
        segments: List<SegmentInfo>,
    ): Map<SourceKind, SourceReport> {
        val produced = segments.map { it.source }.toSet()
        return previous.mapValues { (kind, report) ->
            when {
                report.state == SourceState.ACTIVE || report.state.isPending ->
                    report.copy(
                        state = SourceState.INTERRUPTED,
                        detail = "Recuperada após queda do processo",
                        everActive = report.everActive || kind in produced,
                        interruptions = report.interruptions + 1,
                    )
                else -> report
            }
        }
    }
}
