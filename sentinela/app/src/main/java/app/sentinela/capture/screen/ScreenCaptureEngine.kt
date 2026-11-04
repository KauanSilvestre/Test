package app.sentinela.capture.screen

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import app.sentinela.capture.CaptureEngine
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceState
import app.sentinela.session.controller.SegmentHandle
import app.sentinela.session.controller.SessionLedger

/**
 * Grava a tela a partir de um token de [MediaProjection] já consentido pelo
 * usuário nesta sessão.
 *
 * Este engine **não** inicia nem reutiliza consentimento por conta própria: o
 * token vem sempre de um diálogo mostrado ao usuário na Activity de disparo. Em
 * Android 14+ o token é de uso único e o próprio sistema pode encerrar a
 * projeção (por exemplo ao bloquear a tela); por isso a tela nunca é prometida
 * como captura totalmente automática e o [MediaProjection.Callback] registra a
 * interrupção.
 */
class ScreenCaptureEngine(
    private val context: Context,
    private val projection: MediaProjection,
    private val metrics: DisplayMetrics,
    private val ledger: SessionLedger,
) : CaptureEngine {

    override val source: SourceKind = SourceKind.SCREEN

    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var handle: SegmentHandle? = null
    @Volatile private var stopped = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!stopped) {
                ledger.setSource(source, SourceState.INTERRUPTED, "Projeção encerrada pelo sistema")
                stop()
            }
        }
    }

    override fun start(): CaptureEngine.StartResult {
        return try {
            projection.registerCallback(projectionCallback, null)
            val segment = ledger.openSegment(source)
            handle = segment

            val width = align(metrics.widthPixels)
            val height = align(metrics.heightPixels)
            val recorder = newRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(bitRateFor(width, height))
                setVideoFrameRate(FRAME_RATE)
                setVideoSize(width, height)
                setOutputFile(segment.file.absolutePath)
                prepare()
            }
            this.recorder = recorder
            inputSurface = recorder.surface
            virtualDisplay = projection.createVirtualDisplay(
                "sentinela-screen",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null,
            )
            recorder.start()
            ledger.setSource(source, SourceState.ACTIVE)
            CaptureEngine.StartResult.Active
        } catch (t: Throwable) {
            releaseQuietly()
            CaptureEngine.StartResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    override fun stop() {
        synchronized(this) {
            if (stopped) return
            stopped = true
        }
        val closing = handle
        handle = null
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // Nenhum quadro válido: o ledger descarta o arquivo vazio.
        }
        releaseQuietly()
        if (closing != null) ledger.closeSegment(closing)
    }

    private fun releaseQuietly() {
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        try {
            recorder?.release()
        } catch (_: Throwable) {
        }
        try {
            projection.unregisterCallback(projectionCallback)
        } catch (_: Throwable) {
        }
        virtualDisplay = null
        recorder = null
        inputSurface = null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    private fun bitRateFor(width: Int, height: Int): Int =
        (width * height * FRAME_RATE * BITS_PER_PIXEL).toInt().coerceIn(2_000_000, 12_000_000)

    private fun align(value: Int): Int = value and 0xF.inv() // múltiplo de 16 exigido por muitos encoders

    private companion object {
        const val FRAME_RATE = 30
        const val BITS_PER_PIXEL = 0.12
    }
}
