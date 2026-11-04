package app.sentinela.capture.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.sentinela.capture.CaptureEngine
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceState
import app.sentinela.core.model.VideoQuality
import app.sentinela.session.controller.SegmentHandle
import app.sentinela.session.controller.SessionLedger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Grava uma câmera (traseira ou frontal) com CameraX `VideoCapture`.
 *
 * CameraX emite os eventos [VideoRecordEvent.Start] e [VideoRecordEvent.Finalize];
 * o engine só confirma o estado ACTIVE depois do evento real de início — é esse
 * sinal, e não o toque do usuário, que dispara a confirmação háptica.
 *
 * Deve ser operado na thread principal (o CameraX exige), por isso o bind e o
 * controle de gravação são postados no executor principal.
 */
class CameraCaptureEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val ledger: SessionLedger,
    override val source: SourceKind,
    private val quality: VideoQuality,
    private val withAudio: Boolean,
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context),
) : CaptureEngine {

    @Volatile private var provider: ProcessCameraProvider? = null
    @Volatile private var videoCapture: VideoCapture<Recorder>? = null
    @Volatile private var recording: Recording? = null
    @Volatile private var handle: SegmentHandle? = null
    @Volatile private var stopped = false

    private val lens = when (source) {
        SourceKind.FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun start(): CaptureEngine.StartResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CaptureEngine.StartResult.PermissionDenied
        }
        if (withAudio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Grava vídeo sem faixa de áudio em vez de falhar por inteiro.
            return bind(audio = false)
        }
        return bind(audio = withAudio)
    }

    private fun bind(audio: Boolean): CaptureEngine.StartResult {
        val latch = CountDownLatch(1)
        val result = AtomicReference<CaptureEngine.StartResult>(
            CaptureEngine.StartResult.Failed("Timeout ao abrir a câmera"),
        )
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                if (!cameraProvider.hasCamera(lens)) {
                    result.set(CaptureEngine.StartResult.Unsupported)
                    return@addListener
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.fromOrderedList(qualityFallback(quality)))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, lens, capture)
                videoCapture = capture
                beginRecording(capture, audio)
                result.set(CaptureEngine.StartResult.Active)
            } catch (t: Throwable) {
                result.set(CaptureEngine.StartResult.Failed(t.message ?: t.javaClass.simpleName))
            } finally {
                latch.countDown()
            }
        }, mainExecutor)
        latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return result.get()
    }

    private fun beginRecording(capture: VideoCapture<Recorder>, audio: Boolean) {
        val segment = ledger.openSegment(source)
        handle = segment
        val options = FileOutputOptions.Builder(segment.file).build()
        var pending = capture.output.prepareRecording(context, options)
        // A verificação explícita logo antes de withAudioEnabled() é exigida pelo
        // lint e evita SecurityException se a permissão tiver sido revogada.
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (audio && audioGranted) pending = pending.withAudioEnabled()
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start ->
                    ledger.setSource(source, SourceState.ACTIVE)

                is VideoRecordEvent.Finalize -> {
                    val closing = handle
                    handle = null
                    if (closing != null) ledger.closeSegment(closing)
                    if (!stopped && event.hasError()) {
                        ledger.setSource(
                            source,
                            SourceState.INTERRUPTED,
                            "Erro de gravação de vídeo (código ${event.error})",
                        )
                    }
                }
            }
        }
    }

    override fun stop() {
        stopped = true
        val latch = CountDownLatch(1)
        mainExecutor.execute {
            try {
                recording?.stop()
                recording = null
                provider?.unbindAll()
            } catch (_: Throwable) {
            } finally {
                latch.countDown()
            }
        }
        latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun qualityFallback(quality: VideoQuality): List<Quality> = when (quality) {
        VideoQuality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        VideoQuality.HD -> listOf(Quality.HD, Quality.SD)
        VideoQuality.SD -> listOf(Quality.SD, Quality.HD)
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 8L
    }
}
