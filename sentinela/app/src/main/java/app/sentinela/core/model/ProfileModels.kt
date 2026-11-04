package app.sentinela.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Os quatro perfis do MVP. Cada perfil é só um pedido; a sessão registra o que realmente iniciou. */
@Serializable
enum class ProfileKind {
    AUDIO,
    CAMERA_AUDIO,
    SCREEN_AUDIO,
    COMPLETE,
}

@Serializable
enum class CameraLens { REAR, FRONT, BOTH }

@Serializable
enum class VideoQuality { SD, HD, FHD }

/**
 * Uma fonte de captura independente. Cada fonte tem estado próprio e grava os
 * seus próprios segmentos; a falha de uma nunca derruba as outras.
 */
@Serializable
enum class SourceKind(val filePrefix: String, val isVideo: Boolean) {
    @SerialName("microphone") MICROPHONE("mic", false),
    @SerialName("rear_camera") REAR_CAMERA("rear", true),
    @SerialName("front_camera") FRONT_CAMERA("front", true),
    @SerialName("screen") SCREEN("screen", true),
    @SerialName("playback_audio") PLAYBACK_AUDIO("playback", false),
    ;

    val fileExtension: String get() = if (isVideo) "mp4" else "aac"

    /** Fontes que só existem depois do consentimento de MediaProjection. */
    val needsProjection: Boolean get() = this == SCREEN || this == PLAYBACK_AUDIO

    companion object {
        fun fromFilePrefix(prefix: String): SourceKind? = entries.firstOrNull { it.filePrefix == prefix }
    }
}

@Serializable
data class RecordingProfile(
    val kind: ProfileKind,
    @SerialName("camera_lens") val cameraLens: CameraLens = CameraLens.REAR,
    @SerialName("video_quality") val videoQuality: VideoQuality = VideoQuality.HD,
    @SerialName("segment_minutes") val segmentMinutes: Int = DEFAULT_SEGMENT_MINUTES,
    @SerialName("capture_playback_audio") val capturePlaybackAudio: Boolean = false,
    val vibrate: Boolean = true,
) {
    val usesCamera: Boolean get() = kind == ProfileKind.CAMERA_AUDIO || kind == ProfileKind.COMPLETE
    val usesScreen: Boolean get() = kind == ProfileKind.SCREEN_AUDIO || kind == ProfileKind.COMPLETE

    /** A captura de tela sempre exige o diálogo de consentimento do sistema. */
    val needsScreenConsent: Boolean get() = usesScreen

    val segmentDurationMillis: Long get() = segmentMinutes.coerceIn(1, MAX_SEGMENT_MINUTES) * 60_000L

    /** O que este perfil pede. O microfone ambiente é a fonte principal e está em todos os perfis. */
    fun requestedSources(): Set<SourceKind> = buildSet {
        add(SourceKind.MICROPHONE)
        if (usesCamera) {
            when (cameraLens) {
                CameraLens.REAR -> add(SourceKind.REAR_CAMERA)
                CameraLens.FRONT -> add(SourceKind.FRONT_CAMERA)
                CameraLens.BOTH -> {
                    add(SourceKind.REAR_CAMERA)
                    add(SourceKind.FRONT_CAMERA)
                }
            }
        }
        if (usesScreen) {
            add(SourceKind.SCREEN)
            if (capturePlaybackAudio) add(SourceKind.PLAYBACK_AUDIO)
        }
    }

    companion object {
        const val DEFAULT_SEGMENT_MINUTES = 5
        const val MAX_SEGMENT_MINUTES = 30
        val SEGMENT_CHOICES = listOf(1, 5, 10, 15)

        fun defaultFor(kind: ProfileKind): RecordingProfile = when (kind) {
            ProfileKind.AUDIO -> RecordingProfile(kind)
            ProfileKind.CAMERA_AUDIO -> RecordingProfile(kind, cameraLens = CameraLens.REAR)
            ProfileKind.SCREEN_AUDIO -> RecordingProfile(kind, capturePlaybackAudio = false)
            ProfileKind.COMPLETE -> RecordingProfile(kind, cameraLens = CameraLens.REAR)
        }
    }
}
