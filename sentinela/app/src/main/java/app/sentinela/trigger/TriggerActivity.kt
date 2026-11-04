package app.sentinela.trigger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.sentinela.SentinelaApp
import app.sentinela.core.logging.SLog
import app.sentinela.core.permissions.SentinelaPermissions
import app.sentinela.session.service.CaptureState
import app.sentinela.session.service.RecordingBus
import app.sentinela.session.service.RecordingService

/**
 * Activity mínima e sem UI acionada pelo disparo físico (duplo toque do botão
 * lateral em Galaxy), pelo Quick Settings Tile ou por um atalho. Ela existe por
 * instantes só para fornecer o estado "visível" que o Android 14+ exige para
 * iniciar um foreground service de câmera/microfone a partir do background, e
 * para hospedar o diálogo de consentimento de captura de tela.
 *
 * Semântica de alternância: se já há uma sessão em andamento, o disparo a
 * encerra; caso contrário, inicia uma nova.
 */
class TriggerActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            launchRecordingService(RecordingService.startWithProjectionIntent(this, result.resultCode, result.data!!))
        } else {
            SLog.w("Consentimento de tela negado; iniciando sem captura de tela")
            launchRecordingService(RecordingService.startIntent(this))
        }
        finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disparo em sessão ativa = parar.
        if (RecordingBus.isBusy || RecordingBus.state.value is CaptureState.Active) {
            startService(RecordingService.stopIntent(this))
            finishQuietly()
            return
        }

        val graph = (application as SentinelaApp).graph
        val profile = graph.profileStore.activeRecordingProfile()

        if (!SentinelaPermissions.canAttempt(this, profile)) {
            // Sem o microfone não há captura possível; manda o usuário ao app
            // para conceder a permissão em vez de falhar em silêncio.
            startActivity(
                packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finishQuietly()
            return
        }

        if (profile.needsScreenConsent) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            launchRecordingService(RecordingService.startIntent(this))
            finishQuietly()
        }
    }

    private fun launchRecordingService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun finishQuietly() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun launchIntent(context: Context): Intent =
            Intent(context, TriggerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
