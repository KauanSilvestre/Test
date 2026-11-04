package app.sentinela

import android.app.Application
import app.sentinela.core.DeviceInfoProvider
import app.sentinela.core.logging.SLog
import app.sentinela.core.model.CaptureSupport
import app.sentinela.core.storage.SessionStore
import app.sentinela.data.ProfileStore
import app.sentinela.data.SessionRepository
import app.sentinela.data.room.SentinelaDatabase
import app.sentinela.session.controller.AndroidCaptureEngineFactory
import app.sentinela.session.recovery.RecoveryEngine
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SentinelaApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        graph.runStartupRecovery()
    }
}

/**
 * Localizador de serviços simples (sem framework de DI, apropriado ao tamanho
 * do MVP). Mantém únicas as instâncias de armazenamento e expõe fábricas.
 */
class Graph(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionsRoot: File by lazy { File(app.filesDir, "sessions").apply { mkdirs() } }
    val store: SessionStore by lazy { SessionStore(sessionsRoot) }
    val profileStore: ProfileStore by lazy { ProfileStore(app) }
    val engineFactory by lazy { AndroidCaptureEngineFactory(app) }
    val deviceInfo by lazy { DeviceInfoProvider.collect(app) }

    @Volatile
    var captureSupport: CaptureSupport = CaptureSupport.UNKNOWN

    private val database: SentinelaDatabase by lazy {
        Room.databaseBuilder(app, SentinelaDatabase::class.java, "sentinela-index.db")
            .fallbackToDestructiveMigration() // é só um cache; recriar é seguro
            .build()
    }

    val repository: SessionRepository by lazy { SessionRepository(store, database.sessions()) }

    /** Recupera sessões abertas por uma queda e reconstrói o índice da UI. */
    fun runStartupRecovery() {
        scope.launch {
            runCatching {
                val result = RecoveryEngine(store).recoverAll()
                SLog.i("Recuperação: ${result.recovered.size} abertas, ${result.verifiedIntact.size} íntegras")
                repository.reindex()
            }.onFailure { SLog.e("Falha na recuperação de inicialização", it) }
        }
    }

    fun indexSessionAsync(id: String) {
        scope.launch {
            runCatching { store.readManifest(id)?.let { repository.index(it) } }
                .onFailure { SLog.e("Falha ao indexar $id", it) }
        }
    }
}
