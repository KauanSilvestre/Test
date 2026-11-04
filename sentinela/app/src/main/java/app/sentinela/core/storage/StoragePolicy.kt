package app.sentinela.core.storage

/**
 * Proteção contra disco cheio: vídeo para primeiro, áudio por último,
 * porque o áudio é a fonte mais barata e mais importante.
 */
object StoragePolicy {
    private const val MB = 1024L * 1024L

    const val MIN_BYTES_TO_START: Long = 150 * MB
    const val STOP_VIDEO_BELOW: Long = 500 * MB
    const val STOP_ALL_BELOW: Long = 80 * MB

    enum class Action { NONE, STOP_VIDEO, STOP_ALL }

    fun canStart(usableBytes: Long): Boolean = usableBytes >= MIN_BYTES_TO_START

    fun evaluate(usableBytes: Long): Action = when {
        usableBytes < STOP_ALL_BELOW -> Action.STOP_ALL
        usableBytes < STOP_VIDEO_BELOW -> Action.STOP_VIDEO
        else -> Action.NONE
    }
}
