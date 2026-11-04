package app.sentinela.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SourceState {
    NOT_REQUESTED,
    STARTING,
    AWAITING_CONSENT,
    ACTIVE,
    STOPPED,
    UNSUPPORTED,
    PERMISSION_DENIED,
    USER_DENIED,
    DEVICE_LOCKED,
    INTERRUPTED,
    FAILED,
    ;

    val isPending: Boolean get() = this == STARTING || this == AWAITING_CONSENT
}

@Serializable
data class SourceReport(
    val state: SourceState,
    val detail: String? = null,
    /** A fonte chegou a gravar dados em algum momento da sessão. */
    @SerialName("ever_active") val everActive: Boolean = false,
    /** Quantas vezes a fonte foi interrompida depois de ativa. */
    val interruptions: Int = 0,
)

@Serializable
enum class SessionStatus {
    STARTING,
    ACTIVE,
    STOPPING,
    COMPLETE,
    PARTIAL_SUCCESS,
    FAILED,

    /** O processo morreu durante a sessão; os arquivos foram recuperados do disco. */
    RECOVERED,
    ;

    val isOpen: Boolean get() = this == STARTING || this == ACTIVE || this == STOPPING
}

@Serializable
enum class SegmentStatus {
    RECORDING,
    FINALIZED,
    RECOVERED,

    /** MP4 sem a caixa `moov`: o contêiner não foi fechado e pode não abrir em players comuns. */
    POSSIBLY_CORRUPT,
}

@Serializable
data class SegmentInfo(
    val source: SourceKind,
    val index: Int,
    val file: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val bytes: Long = 0,
    val sha256: String? = null,
    val status: SegmentStatus = SegmentStatus.RECORDING,
)

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    @SerialName("android_sdk") val androidSdk: Int,
    @SerialName("app_version") val appVersion: String,
    val timezone: String,
)

/**
 * O manifesto é a fonte da verdade da sessão. O banco Room é só um índice
 * reconstruível a partir destes arquivos.
 */
@Serializable
data class SessionManifest(
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("session_id") val sessionId: String,
    val profile: RecordingProfile,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val status: SessionStatus,
    val sources: Map<SourceKind, SourceReport>,
    val segments: List<SegmentInfo> = emptyList(),
    val device: DeviceInfo,
    /** Hash da última entrada do journal no momento em que o manifesto foi gravado. */
    @SerialName("journal_head") val journalHead: String? = null,
) {
    val totalBytes: Long get() = segments.sumOf { it.bytes }

    fun source(kind: SourceKind): SourceReport = sources[kind] ?: SourceReport(SourceState.NOT_REQUESTED)

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
