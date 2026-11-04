package app.sentinela.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Índice consultável das sessões. É apenas um cache: a verdade está nos
 * `manifest.json` em disco, e o índice é reconstruído a partir deles na
 * inicialização. Por isso ele pode ser apagado sem perda de evidência.
 */
@Entity(tableName = "session_index")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    val profile: String,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "ended_at") val endedAt: String?,
    val status: String,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "segment_count") val segmentCount: Int,
    @ColumnInfo(name = "sources_summary") val sourcesSummary: String,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM session_index ORDER BY session_id DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session_index WHERE session_id = :id")
    suspend fun find(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SessionEntity>)

    @Query("DELETE FROM session_index WHERE session_id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM session_index")
    suspend fun clear()
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class SentinelaDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
}
