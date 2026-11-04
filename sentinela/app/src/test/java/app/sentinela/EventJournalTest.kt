package app.sentinela

import app.sentinela.core.model.SourceKind
import app.sentinela.core.storage.EventJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class EventJournalTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun fixedClock(): () -> Instant {
        var t = Instant.parse("2026-11-03T21:00:00Z")
        return { t.also { t = t.plusSeconds(1) } }
    }

    @Test
    fun `chain verifies for an untouched journal`() {
        val file = File(tmp.newFolder(), "journal.jsonl")
        val journal = EventJournal(file, fixedClock())
        journal.append("SESSION_START_REQUESTED")
        journal.append("SOURCE_STATE", SourceKind.MICROPHONE, "ACTIVE")
        journal.append("SESSION_FINALIZED", detail = "status=COMPLETE")

        val result = EventJournal.verify(file)
        assertTrue(result.valid)
        assertEquals(3, result.entries)
        assertEquals(null, result.firstBrokenSeq)
    }

    @Test
    fun `first entry links to genesis and head advances`() {
        val file = File(tmp.newFolder(), "journal.jsonl")
        val journal = EventJournal(file, fixedClock())
        val first = journal.append("A")
        assertEquals(EventJournal.GENESIS, first.prev)
        assertEquals(first.hash, journal.head)
        val second = journal.append("B")
        assertEquals(first.hash, second.prev)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun `tampering with a line breaks the chain`() {
        val file = File(tmp.newFolder(), "journal.jsonl")
        val journal = EventJournal(file, fixedClock())
        journal.append("A", detail = "original")
        journal.append("B")
        journal.append("C")

        val lines = file.readLines().toMutableList()
        lines[0] = lines[0].replace("original", "forjado")
        file.writeText(lines.joinToString("\n") + "\n")

        val result = EventJournal.verify(file)
        assertFalse(result.valid)
        assertEquals(0L, result.firstBrokenSeq)
    }

    @Test
    fun `reopening continues sequence and chain`() {
        val folder = tmp.newFolder()
        val file = File(folder, "journal.jsonl")
        EventJournal(file, fixedClock()).apply {
            append("A"); append("B")
        }
        val reopened = EventJournal(file, fixedClock())
        reopened.append("C")
        val result = EventJournal.verify(file)
        assertTrue(result.valid)
        assertEquals(3, result.entries)
        val entries = EventJournal.readAll(file)
        assertEquals(listOf(0L, 1L, 2L), entries.map { it.seq })
    }

    @Test
    fun `a truncated trailing line is tolerated on read`() {
        val file = File(tmp.newFolder(), "journal.jsonl")
        val journal = EventJournal(file, fixedClock())
        journal.append("A")
        journal.append("B")
        // Simula uma queda no meio da escrita de uma terceira linha.
        file.appendText("{\"seq\":2,\"at\":\"2026")
        val entries = EventJournal.readAll(file)
        assertEquals(2, entries.size)
    }
}
