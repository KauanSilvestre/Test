package app.sentinela.session.controller

import android.content.Context
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import androidx.lifecycle.LifecycleOwner
import app.sentinela.capture.CaptureEngine
import app.sentinela.capture.audio.AudioCaptureEngine
import app.sentinela.capture.camera.CameraCaptureEngine
import app.sentinela.capture.screen.ScreenCaptureEngine
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.SourceKind

/** Parâmetros de runtime que o controlador não conhece até o disparo. */
class CaptureContext(
    val lifecycleOwner: LifecycleOwner,
    val projection: MediaProjection?,
    val displayMetrics: DisplayMetrics,
)

/** Cria o engine certo para cada fonte. Abstraído para permitir testes do controlador. */
interface CaptureEngineFactory {
    fun create(
        source: SourceKind,
        profile: RecordingProfile,
        ledger: SessionLedger,
        ctx: CaptureContext,
    ): CaptureEngine?
}

class AndroidCaptureEngineFactory(private val appContext: Context) : CaptureEngineFactory {
    override fun create(
        source: SourceKind,
        profile: RecordingProfile,
        ledger: SessionLedger,
        ctx: CaptureContext,
    ): CaptureEngine? = when (source) {
        SourceKind.MICROPHONE -> AudioCaptureEngine(
            context = appContext,
            ledger = ledger,
            segmentMillis = profile.segmentDurationMillis,
        )

        SourceKind.REAR_CAMERA, SourceKind.FRONT_CAMERA -> CameraCaptureEngine(
            context = appContext,
            lifecycleOwner = ctx.lifecycleOwner,
            ledger = ledger,
            source = source,
            quality = profile.videoQuality,
            withAudio = false, // o microfone ambiente é gravado em faixa própria
        )

        SourceKind.SCREEN -> ctx.projection?.let { projection ->
            ScreenCaptureEngine(
                context = appContext,
                projection = projection,
                metrics = ctx.displayMetrics,
                ledger = ledger,
            )
        }

        // Áudio interno de apps (AudioPlaybackCapture) fica fora do MVP inicial.
        SourceKind.PLAYBACK_AUDIO -> null
    }
}
