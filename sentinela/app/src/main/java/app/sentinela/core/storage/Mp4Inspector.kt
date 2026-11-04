package app.sentinela.core.storage

import java.io.File
import java.io.RandomAccessFile

/**
 * Inspeção mínima de contêiner MP4. Um MP4 só é reproduzível quando a caixa
 * `moov` (índice) foi escrita, o que acontece ao finalizar o arquivo.
 */
object Mp4Inspector {

    fun hasMoov(file: File): Boolean {
        if (!file.isFile || file.length() < 8) return false
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var position = 0L
            val type = ByteArray(4)
            while (position + 8 <= length) {
                raf.seek(position)
                var size = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.readFully(type)
                if (String(type, Charsets.US_ASCII) == "moov") return true
                when (size) {
                    0L -> return false // a caixa vai até o fim do arquivo: não há moov depois dela
                    1L -> {
                        if (position + 16 > length) return false
                        size = raf.readLong()
                    }
                }
                if (size < 8) return false
                position += size
            }
            return false
        }
    }
}
