package app.sentinela.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sentinela.R
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceState

@Composable
fun profileLabel(kind: ProfileKind): String = stringResource(
    when (kind) {
        ProfileKind.AUDIO -> R.string.profile_audio
        ProfileKind.CAMERA_AUDIO -> R.string.profile_camera_audio
        ProfileKind.SCREEN_AUDIO -> R.string.profile_screen_audio
        ProfileKind.COMPLETE -> R.string.profile_complete
    },
)

@Composable
fun sourceLabel(kind: SourceKind): String = stringResource(
    when (kind) {
        SourceKind.MICROPHONE -> R.string.source_microphone
        SourceKind.REAR_CAMERA -> R.string.source_rear_camera
        SourceKind.FRONT_CAMERA -> R.string.source_front_camera
        SourceKind.SCREEN -> R.string.source_screen
        SourceKind.PLAYBACK_AUDIO -> R.string.source_playback_audio
    },
)

@Composable
fun sourceStateLabel(state: SourceState): String = stringResource(
    when (state) {
        SourceState.NOT_REQUESTED -> R.string.state_not_requested
        SourceState.STARTING -> R.string.state_starting
        SourceState.AWAITING_CONSENT -> R.string.state_awaiting_consent
        SourceState.ACTIVE -> R.string.state_active
        SourceState.STOPPED -> R.string.state_stopped
        SourceState.UNSUPPORTED -> R.string.state_unsupported
        SourceState.PERMISSION_DENIED -> R.string.state_permission_denied
        SourceState.USER_DENIED -> R.string.state_user_denied
        SourceState.DEVICE_LOCKED -> R.string.state_device_locked
        SourceState.INTERRUPTED -> R.string.state_interrupted
        SourceState.FAILED -> R.string.state_failed
    },
)

fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
    return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
}
