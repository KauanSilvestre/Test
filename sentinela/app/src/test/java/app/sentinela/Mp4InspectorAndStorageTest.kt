package app.sentinela

import app.sentinela.core.storage.Mp4Inspector
import app.sentinela.core.storage.SessionStore
import app.sentinela.core.storage.StoragePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class Mp4InspectorAndStorageTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Monta um MP4 mínimo com as caixas `ftyp` e, opcionalmente, `moov`. */
    private fun writeMp4(file: File, withMoov: Boolean) {
        RandomAccessFile(file, "rw").use { raf ->
            fun box(type: String, payload: ByteArray) {
                raf.writeInt(payload.size + 8)
                raf.write(type.toByteArray(Charsets.US_ASCII))
                raf.write(payload)
            }
            box("ftyp", ByteArray(16))
            box("mdat", ByteArray(64))
            if (withMoov) box("moov", ByteArray(32))
        }
    }

    @Test
    fun `detects a finalized mp4 with a moov box`() {
        val file = File(tmp.newFolder(), "rear_0001.mp4")
        writeMp4(file, withMoov = true)
        assertTrue(Mp4Inspector.hasMoov(file))
    }

    @Test
    fun `flags an mp4 without moov as not finalized`() {
        val file = File(tmp.newFolder(), "rear_0001.mp4")
        writeMp4(file, withMoov = false)
        assertFalse(Mp4Inspector.hasMoov(file))
    }

    @Test
    fun `empty or tiny files are not considered finalized`() {
        val file = File(tmp.newFolder(), "rear_0001.mp4")
        file.writeBytes(ByteArray(3))
        assertFalse(Mp4Inspector.hasMoov(file))
    }

    @Test
    fun `segment filename regex matches expected names`() {
        assertTrue(SessionStore.SEGMENT_FILE.matches("mic_0001.aac"))
        assertTrue(SessionStore.SEGMENT_FILE.matches("rear_0042.mp4"))
        assertTrue(SessionStore.SEGMENT_FILE.matches("screen_9999.mp4"))
        assertFalse(SessionStore.SEGMENT_FILE.matches("manifest.json"))
        assertFalse(SessionStore.SEGMENT_FILE.matches("mic_1.aac"))
    }

    @Test
    fun `storage policy escalates as space shrinks`() {
        assertEquals(StoragePolicy.Action.NONE, StoragePolicy.evaluate(2_000L * 1024 * 1024))
        assertEquals(StoragePolicy.Action.STOP_VIDEO, StoragePolicy.evaluate(200L * 1024 * 1024))
        assertEquals(StoragePolicy.Action.STOP_ALL, StoragePolicy.evaluate(10L * 1024 * 1024))
    }

    @Test
    fun `cannot start without the minimum free space`() {
        assertTrue(StoragePolicy.canStart(1_000L * 1024 * 1024))
        assertFalse(StoragePolicy.canStart(10L * 1024 * 1024))
    }
}
