package app.sentinela.session.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.sentinela.R
import app.sentinela.SentinelaApp
import app.sentinela.core.logging.SLog
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.SessionOutcome
import app.sentinela.core.permissions.SentinelaPermissions
import app.sentinela.core.storage.JournalEvents
import app.sentinela.core.storage.StoragePolicy
import app.sentinela.session.Haptics
import app.sentinela.session.controller.CaptureContext
import app.sentinela.session.controller.SessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serviço de primeiro plano que hospeda a sessão. É um [LifecycleService]
 * porque o CameraX precisa de um `LifecycleOwner`. Depois de iniciado a partir
 * do disparo (uma Activity brevemente visível), ele continua capturando em
 * segundo plano e com a tela apagada, dentro das regras do Android.
 */
class RecordingService : LifecycleService() {

    private val graph get() = (application as SentinelaApp).graph
    private lateinit var controller: SessionController
    private lateinit var haptics: Haptics
    private var projection: MediaProjection? = null
    private var monitoring = false

    override fun onCreate() {
        super.onCreate()
        haptics = Haptics(this)
        controller = SessionController(
            store = graph.store,
            factory = graph.engineFactory,
            device = graph.deviceInfo,
            support = graph.captureSupport,
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (RecordingBus.isBusy) {
            SLog.w("Start ignorado: já há sessão em andamento")
            return
        }
        val profile = graph.profileStore.activeRecordingProfile()
        if (!SentinelaPermissions.canAttempt(this, profile)) {
            RecordingBus.set(CaptureState.Failed("Permissão de microfone ausente"))
            stopSelfSafely()
            return
        }
        RecordingBus.set(CaptureState.Starting)

        // Em Android 14+, startForeground precisa ocorrer logo e declarar os
        // tipos de serviço correspondentes às fontes realmente usadas.
        val hasProjectionConsent = intent.hasExtra(EXTRA_RESULT_CODE)
        val types = computeServiceTypes(profile, hasProjectionConsent)
        startForegroundCompat(getString(R.string.notif_starting), types)

        // O token de MediaProjection só pode ser obtido após startForeground
        // com o tipo mediaProjection presente.
        if (hasProjectionConsent) {
            projection = obtainProjection(intent)
        }

        lifecycleScope.launch {
            val ctx = CaptureContext(
                lifecycleOwner = this@RecordingService,
                projection = projection,
                displayMetrics = currentMetrics(),
            )
            val result = withContext(Dispatchers.IO) { controller.start(profile, ctx) }
            onStarted(profile, result)
        }
    }

    private fun onStarted(profile: RecordingProfile, result: SessionController.StartResult) {
        RecordingBus.set(CaptureState.Active(result.manifest))
        graph.indexSessionAsync(result.manifest.sessionId)
        if (profile.vibrate) {
            when (result.feedback) {
                SessionOutcome.StartFeedback.ALL_ACTIVE -> haptics.confirmedStart()
                SessionOutcome.StartFeedback.PARTIAL -> haptics.confirmedStart()
                SessionOutcome.StartFeedback.NOTHING_ACTIVE -> haptics.failure()
            }
        }
        if (result.feedback == SessionOutcome.StartFeedback.NOTHING_ACTIVE) {
            SLog.w("Nenhuma fonte ativa; encerrando sessão vazia")
            handleStop()
            return
        }
        updateNotification(describeActive(result), showStop = true)
        startStorageMonitor()
    }

    private fun handleStop() {
        if (!RecordingBus.isBusy && controller.activeLedger == null) {
            stopSelfSafely()
            return
        }
        RecordingBus.set(CaptureState.Stopping)
        val profile = graph.profileStore.activeRecordingProfile()
        lifecycleScope.launch {
            val manifest = withContext(Dispatchers.IO) { controller.stop() }
            projection?.stop()
            projection = null
            if (manifest != null) {
                graph.indexSessionAsync(manifest.sessionId)
                if (profile.vibrate) haptics.stopped()
                RecordingBus.set(CaptureState.Finished(manifest))
            } else {
                RecordingBus.set(CaptureState.Idle)
            }
            stopSelfSafely()
        }
    }

    /** Vigia o espaço em disco; para o vídeo antes do áudio quando aperta. */
    private fun startStorageMonitor() {
        if (monitoring) return
        monitoring = true
        lifecycleScope.launch {
            while (RecordingBus.state.value is CaptureState.Active) {
                when (StoragePolicy.evaluate(graph.store.usableBytes())) {
                    StoragePolicy.Action.STOP_ALL -> {
                        controller.activeLedger?.event(JournalEvents.LOW_STORAGE, detail = "crítico; encerrando")
                        handleStop()
                        break
                    }
                    StoragePolicy.Action.STOP_VIDEO -> {
                        controller.activeLedger?.event(JournalEvents.LOW_STORAGE, detail = "baixo; parando vídeo")
                        withContext(Dispatchers.IO) { controller.stopVideoSources() }
                    }
                    StoragePolicy.Action.NONE -> Unit
                }
                delay(STORAGE_POLL_MILLIS)
            }
            monitoring = false
        }
    }

    private fun computeServiceTypes(profile: RecordingProfile, projectionConsent: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        if (SentinelaPermissions.isGranted(this, android.Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (profile.usesCamera && SentinelaPermissions.isGranted(this, android.Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (projectionConsent) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        return types
    }

    private fun startForegroundCompat(text: String, types: Int) {
        val notification = RecordingNotifications.build(this, text, showStop = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types != 0) {
            startForeground(RecordingNotifications.NOTIFICATION_ID, notification, types)
        } else {
            startForeground(RecordingNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String, showStop: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(RecordingNotifications.NOTIFICATION_ID, RecordingNotifications.build(this, text, showStop))
    }

    private fun obtainProjection(intent: Intent): MediaProjection? {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        } ?: return null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return runCatching { manager.getMediaProjection(resultCode, data) }
            .onFailure { SLog.e("Falha ao obter MediaProjection", it) }
            .getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun currentMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
            metrics.density = resources.displayMetrics.density
        } else {
            wm.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics
    }

    private fun describeActive(result: SessionController.StartResult): String {
        val active = result.manifest.sources.count { it.value.state.name == "ACTIVE" }
        return resources.getQuantityString(R.plurals.notif_active_sources, active, active)
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "app.sentinela.action.START"
        const val ACTION_STOP = "app.sentinela.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val STORAGE_POLL_MILLIS = 15_000L

        fun startIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_START)

        fun startWithProjectionIntent(context: Context, resultCode: Int, data: Intent): Intent =
            startIntent(context)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
    }
}
