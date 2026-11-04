package app.sentinela.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.sentinela.core.model.RecordingProfile

/** Centraliza quais permissões cada perfil exige e quais o aparelho já concedeu. */
object SentinelaPermissions {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Permissões de runtime a pedir antes de iniciar o perfil (sem contar o consentimento de tela). */
    fun requiredRuntime(profile: RecordingProfile): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (profile.usesCamera) add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun missing(context: Context, profile: RecordingProfile): List<String> =
        requiredRuntime(profile).filterNot { isGranted(context, it) }

    /** O mínimo para o perfil ter alguma chance de gravar (o microfone é a fonte principal). */
    fun canAttempt(context: Context, profile: RecordingProfile): Boolean =
        isGranted(context, Manifest.permission.RECORD_AUDIO)
}
