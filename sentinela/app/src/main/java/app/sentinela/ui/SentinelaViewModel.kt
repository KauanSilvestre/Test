package app.sentinela.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sentinela.SentinelaApp
import app.sentinela.capture.camera.CameraSupportProbe
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.SessionManifest
import app.sentinela.data.room.SessionEntity
import app.sentinela.session.service.CaptureState
import app.sentinela.session.service.RecordingBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SentinelaViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SentinelaApp).graph

    val captureState: StateFlow<CaptureState> = RecordingBus.state

    val sessions: StateFlow<List<SessionEntity>> = graph.repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeProfile = MutableStateFlow(graph.profileStore.activeProfile)
    val activeProfile: StateFlow<ProfileKind> = _activeProfile.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            graph.captureSupport = CameraSupportProbe.probe(getApplication())
        }
    }

    fun profile(kind: ProfileKind): RecordingProfile = graph.profileStore.load(kind)

    fun saveProfile(profile: RecordingProfile) = graph.profileStore.save(profile)

    fun setActiveProfile(kind: ProfileKind) {
        graph.profileStore.activeProfile = kind
        _activeProfile.value = kind
    }

    fun manifestOf(id: String): SessionManifest? = graph.repository.readManifest(id)

    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) { graph.repository.delete(id) }

    fun captureSupport() = graph.captureSupport
}
