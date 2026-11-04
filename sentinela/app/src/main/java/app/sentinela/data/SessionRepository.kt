package app.sentinela.data

import app.sentinela.core.model.SessionManifest
import app.sentinela.core.storage.SessionStore
import app.sentinela.data.room.SessionDao
import app.sentinela.data.room.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Faz a ponte entre os manifestos em disco (verdade) e o índice Room (cache
 * consultável pela UI). [reindex] reconstrói o índice lendo todos os manifestos.
 */
class SessionRepository(
    private val store: SessionStore,
    private val dao: SessionDao,
) {
    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeAll()

    fun readManifest(id: String): SessionManifest? = store.readManifest(id)

    suspend fun index(manifest: SessionManifest) = dao.upsert(manifest.toEntity())

    suspend fun reindex() {
        val entities = store.listSessionIds().mapNotNull { store.readManifest(it)?.toEntity() }
        dao.clear()
        dao.upsertAll(entities)
    }

    suspend fun delete(id: String) {
        store.delete(id)
        dao.delete(id)
    }

    private fun SessionManifest.toEntity() = SessionEntity(
        sessionId = sessionId,
        profile = profile.kind.name,
        startedAt = startedAt,
        endedAt = endedAt,
        status = status.name,
        totalBytes = totalBytes,
        segmentCount = segments.size,
        sourcesSummary = sources.entries.joinToString(",") { "${it.key.filePrefix}=${it.value.state.name}" },
    )
}
