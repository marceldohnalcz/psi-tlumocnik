package io.github.marciano.psitlumocnik

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.marciano.psitlumocnik.analysis.Analyzer
import io.github.marciano.psitlumocnik.data.Repository
import io.github.marciano.psitlumocnik.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

data class WatchState(
    val running: Boolean = false,
    val dogId: String? = null,
    val startedAt: Long = 0L,
    val clips: Int = 0,
    val capturing: Boolean = false,
    val level: Float = 0f,
    val error: String? = null,
)

class PsiApp : Application() {

    lateinit var repository: Repository; private set
    lateinit var settings: SettingsStore; private set
    lateinit var analyzer: Analyzer; private set
    val watchState = MutableStateFlow(WatchState())

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = Repository(this)
        settings = SettingsStore(this)
        analyzer = Analyzer(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Hlídání", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Běžící poslech štěkotu, když nejsi doma"
            }
        )
    }

    companion object {
        const val CHANNEL_WATCH = "watch"
        lateinit var instance: PsiApp; private set
    }
}
