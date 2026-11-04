package app.sentinela.data

import android.content.Context
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.storage.JsonFormats

/**
 * Guarda a configuração de cada perfil e qual é o perfil ativo (o que o
 * disparo físico usa). Persistido em SharedPreferences como JSON.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("sentinela_profiles", Context.MODE_PRIVATE)

    fun load(kind: ProfileKind): RecordingProfile {
        val raw = prefs.getString(key(kind), null) ?: return RecordingProfile.defaultFor(kind)
        return runCatching {
            JsonFormats.compact.decodeFromString(RecordingProfile.serializer(), raw)
        }.getOrDefault(RecordingProfile.defaultFor(kind))
    }

    fun save(profile: RecordingProfile) {
        prefs.edit()
            .putString(key(profile.kind), JsonFormats.compact.encodeToString(RecordingProfile.serializer(), profile))
            .apply()
    }

    var activeProfile: ProfileKind
        get() = runCatching { ProfileKind.valueOf(prefs.getString(ACTIVE, null) ?: "") }
            .getOrDefault(ProfileKind.AUDIO)
        set(value) = prefs.edit().putString(ACTIVE, value.name).apply()

    fun activeRecordingProfile(): RecordingProfile = load(activeProfile)

    private fun key(kind: ProfileKind) = "profile_${kind.name}"

    private companion object {
        const val ACTIVE = "active_profile"
    }
}
