package app.sentinela.session.service

import app.sentinela.core.model.SessionManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de captura observável pela UI, vivo enquanto o processo existir. */
sealed interface CaptureState {
    data object Idle : CaptureState
    data object Starting : CaptureState
    data class Active(val manifest: SessionManifest) : CaptureState
    data object Stopping : CaptureState
    data class Finished(val manifest: SessionManifest) : CaptureState
    data class Failed(val reason: String) : CaptureState
}

/** Ponte de estado entre o serviço de primeiro plano e a UI. */
object RecordingBus {
    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun set(state: CaptureState) { _state.value = state }

    val isBusy: Boolean
        get() = _state.value is CaptureState.Active ||
            _state.value is CaptureState.Starting ||
            _state.value is CaptureState.Stopping
}
