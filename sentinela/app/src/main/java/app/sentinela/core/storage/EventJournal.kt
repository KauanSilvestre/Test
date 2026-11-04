package app.sentinela.core.storage

import app.sentinela.core.hashing.Sha256
import app.sentinela.core.model.SourceKind
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@Serializable
data class JournalEntry(
    val seq: Long,
    val at: String,
    val type: String,
    val source: SourceKind? = null,
    val detail: String? = null,
    val prev: String,
    val hash: String,
)

/**
 * Journal de eventos somente-anexação (JSON Lines) encadeado por SHA-256.
 *
 * Cada entrada carrega o hash da anterior; alterar, remover ou reordenar
 * qualquer linha quebra a cadeia, o que [verify] detecta. Cada anexação faz
 * `fsync`, então um evento confirmado sobrevive a uma queda do processo.
 */
class EventJournal(
    private val file: File,
    private val clock: () -> Instant = Instant::now,
) {
    private var lastHash: String
    private var nextSeq: Long

    init {
        val existing = readAll(file)
        lastHash = existing.lastOrNull()?.hash ?: GENESIS
        nextSeq = (existing.lastOrNull()?.seq ?: -1L) + 1L
    }

    val head: String @Synchronized get() = lastHash

    @Synchronized
    fun append(type: String, source: SourceKind? = null, detail: String? = null): JournalEntry {
        val at = clock().toString()
        val hash = Sha256.ofString(canonical(nextSeq, at, type, source, detail, lastHash))
        val entry = JournalEntry(nextSeq, at, type, source, detail, lastHash, hash)
        val line = JsonFormats.compact.encodeToString(JournalEntry.serializer(), entry) + "\n"
        FileOutputStream(file, true).use { out ->
            out.write(line.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        lastHash = hash
        nextSeq += 1
        return entry
    }

    data class Verification(val entries: Int, val valid: Boolean, val firstBrokenSeq: Long?)

    companion object {
        val GENESIS: String = "0".repeat(64)

        /** Forma canônica que é hasheada. Mudá-la invalida journals existentes. */
        fun canonical(seq: Long, at: String, type: String, source: SourceKind?, detail: String?, prev: String): String =
            listOf(seq.toString(), at, type, source?.name.orEmpty(), detail.orEmpty(), prev).joinToString("\n")

        /** Lê o journal tolerando uma última linha truncada por queda durante a escrita. */
        fun readAll(file: File): List<JournalEntry> {
            if (!file.exists()) return emptyList()
            return file.readLines(Charsets.UTF_8)
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching { JsonFormats.compact.decodeFromString(JournalEntry.serializer(), line) }.getOrNull()
                }
        }

        fun verify(file: File): Verification {
            if (!file.exists()) return Verification(0, false, null)
            val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            var prev = GENESIS
            var expectedSeq = 0L
            lines.forEachIndexed { i, line ->
                val entry = runCatching { JsonFormats.compact.decodeFromString(JournalEntry.serializer(), line) }.getOrNull()
                    ?: return Verification(i, false, expectedSeq)
                val recomputed = Sha256.ofString(
                    canonical(entry.seq, entry.at, entry.type, entry.source, entry.detail, entry.prev),
                )
                if (entry.seq != expectedSeq || entry.prev != prev || entry.hash != recomputed) {
                    return Verification(i, false, entry.seq)
                }
                prev = entry.hash
                expectedSeq += 1
            }
            return Verification(lines.size, true, null)
        }
    }
}

/** Tipos de evento gravados no journal. */
object JournalEvents {
    const val SESSION_START_REQUESTED = "SESSION_START_REQUESTED"
    const val SESSION_ACTIVE = "SESSION_ACTIVE"
    const val STOP_REQUESTED = "STOP_REQUESTED"
    const val SESSION_FINALIZED = "SESSION_FINALIZED"
    const val SEGMENT_OPENED = "SEGMENT_OPENED"
    const val SEGMENT_FINALIZED = "SEGMENT_FINALIZED"
    const val SEGMENT_DISCARDED_EMPTY = "SEGMENT_DISCARDED_EMPTY"
    const val SOURCE_STATE = "SOURCE_STATE"
    const val AUDIO_SILENCED = "AUDIO_SILENCED"
    const val AUDIO_RESUMED = "AUDIO_RESUMED"
    const val SCREEN_CONTENT_CHANGED = "SCREEN_CONTENT_CHANGED"
    const val LOW_STORAGE = "LOW_STORAGE"
    const val RECOVERY_STARTED = "RECOVERY_STARTED"
    const val RECOVERY_SEGMENT = "RECOVERY_SEGMENT"
    const val RECOVERY_FINISHED = "RECOVERY_FINISHED"
}
