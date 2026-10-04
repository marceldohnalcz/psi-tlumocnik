package io.github.marciano.psitlumocnik.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.marciano.psitlumocnik.MainActivity
import io.github.marciano.psitlumocnik.PsiApp
import io.github.marciano.psitlumocnik.R
import io.github.marciano.psitlumocnik.analysis.BarkAnalysis
import io.github.marciano.psitlumocnik.analysis.Dsp
import io.github.marciano.psitlumocnik.analysis.YamNet
import io.github.marciano.psitlumocnik.audio.FloatList
import io.github.marciano.psitlumocnik.audio.MicStream
import io.github.marciano.psitlumocnik.audio.Wav
import io.github.marciano.psitlumocnik.data.Recording
import io.github.marciano.psitlumocnik.data.Repository
import io.github.marciano.psitlumocnik.data.Situation
import io.github.marciano.psitlumocnik.data.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hlídání: mikrofon běží na pozadí, při štěkotu se automaticky uloží klip
 * (2 s před, dokud pes štěká, + 4 s ticha, max 60 s), rozebere a přidá k psovi.
 */
class WatchService : LifecycleService() {

    private val app get() = application as PsiApp
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var dogId: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val id = intent?.getStringExtra(EXTRA_DOG) ?: run { stopSelf(); return START_NOT_STICKY }
        dogId = id
        ServiceCompat.startForeground(this, NOTIF_ID, notification(0, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PsiTlumocnik:watch").apply { acquire(12 * 60 * 60 * 1000L) }
        app.watchState.value = app.watchState.value.copy(running = true, dogId = id, startedAt = System.currentTimeMillis(), clips = 0, error = null)
        if (job == null) job = lifecycleScope.launch(Dispatchers.IO) { listen() }
        return START_NOT_STICKY
    }

    private suspend fun listen() {
        val mic = MicStream()
        val sensitivity = app.settings.state.value.sensitivity
        val thr = BarkAnalysis.thresholdDb(sensitivity)
        val preRollBlocks = 20                  // 2 s
        val tailBlocks = 40                     // 4 s ticha ukončí klip
        val maxBlocks = 600                     // 60 s
        val preRoll = ArrayDeque<FloatArray>()
        var noiseFloor = Float.NaN
        var capture: FloatList? = null
        var capturedBlocks = 0
        var silentBlocks = 0
        var confirmed = false
        val net: YamNet? = app.analyzer.yamnetOrNull()
        val recent = ArrayDeque<FloatArray>()  // posledních ~1 s pro YAMNet

        try {
            mic.run { samples ->
                val db = Dsp.rmsDb(samples, 0, samples.size)
                if (noiseFloor.isNaN()) noiseFloor = db
                val loud = db > noiseFloor + thr && db > -55f
                // Šumové pozadí: rychle dolů, pomalu nahoru (jen v tichu)
                if (db < noiseFloor) noiseFloor = db else if (!loud) noiseFloor += 0.05f * (db - noiseFloor)
                recent.addLast(samples); if (recent.size > 10) recent.removeFirst()
                app.watchState.update { it.copy(level = mic.level.value) }

                val cap = capture
                if (cap == null) {
                    preRoll.addLast(samples); if (preRoll.size > preRollBlocks) preRoll.removeFirst()
                    if (loud) {
                        // Ověř přes YAMNet, že je to opravdu pes (když AI není, bere se každý hluk)
                        val isDog = net == null || net.dogScore(concat(recent)) > 0.2f
                        if (isDog) {
                            capture = FloatList().also { c -> preRoll.forEach { c.addAll(it) } }
                            preRoll.clear()
                            capturedBlocks = 0; silentBlocks = 0; confirmed = true
                            app.watchState.update { it.copy(capturing = true) }
                        }
                    }
                } else {
                    cap.addAll(samples)
                    capturedBlocks++
                    silentBlocks = if (loud) 0 else silentBlocks + 1
                    if (silentBlocks >= tailBlocks || capturedBlocks >= maxBlocks) {
                        val data = cap.toArray()
                        capture = null
                        app.watchState.update { it.copy(capturing = false) }
                        if (confirmed) lifecycleScope.launch(Dispatchers.Default) { saveClip(data) }
                    }
                }
            }
        } catch (e: Exception) {
            app.watchState.update { it.copy(error = e.message ?: "Mikrofon selhal") }
            stopSelf()
        }
    }

    private fun concat(blocks: Collection<FloatArray>): FloatArray {
        val out = FloatArray(YamNet.WINDOW)
        var pos = 0
        for (b in blocks.reversed()) {
            val n = minOf(b.size, YamNet.WINDOW - pos)
            if (n <= 0) break
            System.arraycopy(b, b.size - n, out, YamNet.WINDOW - pos - n, n)
            pos += n
        }
        return out
    }

    private suspend fun saveClip(samples: FloatArray) {
        val repo = app.repository
        val settings = app.settings.state.value
        val analysis = app.analyzer.analyze(samples, settings.sensitivity)
        if (!analysis.hasBarks) return
        val id = Repository.newId()
        Wav.write(repo.audioFile(id), samples)
        repo.addRecording(
            Recording(
                id = id, dogId = dogId, createdAt = System.currentTimeMillis(),
                durationMs = analysis.durationMs, source = Source.HLIDANI,
                situation = if (settings.watchAutoLabel) Situation.SAMOTA else null,
                autoLabeled = settings.watchAutoLabel,
                analysis = analysis,
            )
        )
        val clips = app.watchState.value.clips + 1
        app.watchState.update { it.copy(clips = clips) }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(clips, false))
    }

    private fun notification(clips: Int, capturing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WatchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val dogName = app.repository.dog(if (::dogId.isInitialized) dogId else "")?.name ?: "pes"
        return NotificationCompat.Builder(this, PsiApp.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Hlídám: $dogName")
            .setContentText(if (clips == 0) "Zatím ticho. Hodný pes." else "Zachyceno štěkání: $clips×")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Ukončit hlídání", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .also { if (capturing) it.setSubText("nahrávám…") }
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        app.watchState.update { it.copy(running = false, capturing = false, level = 0f) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DOG = "dogId"
        const val ACTION_STOP = "stop"
        private const val NOTIF_ID = 42

        fun start(context: Context, dogId: String) {
            context.startForegroundService(Intent(context, WatchService::class.java).putExtra(EXTRA_DOG, dogId))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WatchService::class.java).setAction(ACTION_STOP))
        }
    }
}
