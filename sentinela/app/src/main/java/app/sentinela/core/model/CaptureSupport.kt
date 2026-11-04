package app.sentinela.core.model

import kotlinx.serialization.Serializable

/** O que este aparelho realmente suporta. Preenchido em runtime por consultas de capacidade. */
@Serializable
data class CaptureSupport(
    val hasRearCamera: Boolean,
    val hasFrontCamera: Boolean,
    val concurrentCameras: Boolean,
    val canCaptureScreen: Boolean,
) {
    fun supports(source: SourceKind): Boolean = when (source) {
        SourceKind.MICROPHONE -> true
        SourceKind.REAR_CAMERA -> hasRearCamera
        SourceKind.FRONT_CAMERA -> hasFrontCamera
        SourceKind.SCREEN -> canCaptureScreen
        SourceKind.PLAYBACK_AUDIO -> canCaptureScreen
    }

    companion object {
        val UNKNOWN = CaptureSupport(
            hasRearCamera = true,
            hasFrontCamera = true,
            concurrentCameras = false,
            canCaptureScreen = true,
        )
    }
}
