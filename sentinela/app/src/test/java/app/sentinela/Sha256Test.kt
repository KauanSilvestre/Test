package app.sentinela

import app.sentinela.core.hashing.Sha256
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256Test {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `known vector for empty string`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.ofString(""),
        )
    }

    @Test
    fun `known vector for abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.ofString("abc"),
        )
    }

    @Test
    fun `file hash matches byte hash for the same content`() {
        val file = File(tmp.newFolder(), "segment.bin")
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        file.writeBytes(bytes)
        assertEquals(Sha256.ofBytes(bytes), Sha256.ofFile(file))
    }
}
