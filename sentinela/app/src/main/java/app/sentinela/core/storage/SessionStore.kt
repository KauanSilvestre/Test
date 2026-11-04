package app.sentinela.core.storage

import app.sentinela.core.model.SessionIds
import app.sentinela.core.model.SessionManifest
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Layout em disco (armazenamento privado do app):
 *
 * ```
 * sessions/<session_id>/
 *     manifest.json     estado da sessão (fonte da verdade)
 *     journal.jsonl     eventos encadeados por hash
 *     mic_0001.aac      segmentos de cada fonte
 *     rear_0001.mp4
 * ```
 */
class SessionStore(val root: File) {

    fun sessionDir(id: String): File = File(root, id)

    fun manifestFile(id: String): File = File(sessionDir(id), MANIFEST)

    fun journalFile(id: String): File = File(sessionDir(id), JOURNAL)

    fun createSessionDir(id: String): File {
        require(SessionIds.isValid(id)) { "ID de sessão inválido: $id" }
        val dir = sessionDir(id)
        check(dir.mkdirs() || dir.isDirectory) { "Não foi possível criar ${dir.path}" }
        return dir
    }

    /** Escrita atômica: grava num temporário, faz fsync e renomeia por cima do manifesto. */
    @Synchronized
    fun writeManifest(manifest: SessionManifest) {
        val target = manifestFile(manifest.sessionId)
        val tmp = File(target.parentFile, "$MANIFEST.tmp")
        val bytes = JsonFormats.manifest.encodeToString(SessionManifest.serializer(), manifest)
            .toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun readManifest(id: String): SessionManifest? {
        val file = manifestFile(id)
        if (!file.exists()) return null
        return runCatching {
            JsonFormats.manifest.decodeFromString(SessionManifest.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun listSessionIds(): List<String> =
        root.listFiles()
            ?.filter { it.isDirectory && SessionIds.isValid(it.name) }
            ?.map { it.name }
            ?.sortedDescending()
            .orEmpty()

    fun delete(id: String): Boolean {
        require(SessionIds.isValid(id)) { "ID de sessão inválido: $id" }
        return sessionDir(id).deleteRecursively()
    }

    fun usableBytes(): Long {
        root.mkdirs()
        return root.usableSpace
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val JOURNAL = "journal.jsonl"

        /** Nome de arquivo de segmento: `<prefixo>_<índice de 4 dígitos>.<extensão>`. */
        val SEGMENT_FILE = Regex("""^(mic|rear|front|screen|playback)_(\d{4})\.(aac|mp4)$""")
    }
}
