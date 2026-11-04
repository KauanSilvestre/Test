package app.sentinela.capture.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import app.sentinela.capture.CaptureEngine
import app.sentinela.core.model.SourceKind
import app.sentinela.session.controller.SegmentHandle
import app.sentinela.session.controller.SessionLedger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Microfone ambiente, usando a API de alto nível [MediaRecorder].
 *
 * A saída é AAC em contêiner ADTS (`OutputFormat.AAC_ADTS`): cada quadro é
 * autossuficiente, então um arquivo interrompido por uma queda do processo
 * continua reproduzível até o último quadro gravado — não depende de um índice
 * final como o MP4.
 *
 * A gravação é dividida em segmentos de duração fixa parando um recorder e
 * abrindo o próximo. Isso limita quanto se perde num encerramento abrupto e
 * mantém cada arquivo pequeno o bastante para hash e cópia rápidos.
 */
class AudioCaptureEngine(
    private val context: Context,
    private val ledger: SessionLedger,
    private val segmentMillis: Long,
    override val source: SourceKind = SourceKind.MICROPHONE,
) : CaptureEngine {

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "sentinela-audio") }

    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var current: SegmentHandle? = null
    @Volatile private var running = false

    override fun start(): CaptureEngine.StartResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CaptureEngine.StartResult.PermissionDenied
        }
        return try {
            synchronized(this) {
                running = true
                rollSegment()
            }
            scheduler.scheduleAtFixedRate(::rotate, segmentMillis, segmentMillis, TimeUnit.MILLISECONDS)
            CaptureEngine.StartResult.Active
        } catch (t: Throwable) {
            running = false
            safeRelease()
            CaptureEngine.StartResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun rotate() {
        synchronized(this) {
            if (!running) return
            try {
                closeCurrent()
                rollSegment()
            } catch (t: Throwable) {
                running = false
                safeRelease()
                ledger.setSource(
                    SourceKind.MICROPHONE,
                    app.sentinela.core.model.SourceState.INTERRUPTED,
                    t.message ?: "Falha ao trocar de segmento",
                )
            }
        }
    }

    private fun rollSegment() {
        val handle = ledger.openSegment(source)
        val rec = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            setAudioChannels(1)
            setOutputFile(handle.file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        current = handle
    }

    private fun closeCurrent() {
        val rec = recorder ?: return
        val handle = current
        try {
            rec.stop()
        } catch (_: RuntimeException) {
            // stop() lança se nenhum quadro válido foi capturado; o ledger
            // descarta o arquivo vazio ao fechar o segmento.
        } finally {
            rec.release()
            recorder = null
            current = null
            if (handle != null) ledger.closeSegment(handle)
        }
    }

    override fun stop() {
        synchronized(this) {
            if (!running) return
            running = false
        }
        scheduler.shutdown()
        scheduler.awaitTermination(5, TimeUnit.SECONDS)
        synchronized(this) { closeCurrent() }
    }

    private fun safeRelease() {
        try {
            recorder?.release()
        } catch (_: Throwable) {
        }
        recorder = null
        val handle = current
        current = null
        if (handle != null) ledger.closeSegment(handle)
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 96_000
    }
}
