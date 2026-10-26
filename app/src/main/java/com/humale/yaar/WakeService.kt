package com.humale.yaar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** "हे यार" भनेपछि सुन्ने पृष्ठभूमि सेवा */
class WakeService : Service() {

    private lateinit var voice: VoiceIO
    private lateinit var brain: Brain
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var alive = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("wake", "वेक वर्ड", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "wake")
            .setContentTitle("हुमाले यार सुनिरहेको छ")
            .setContentText("\"हे यार\" भनेर बोलाउनुस्")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        voice = VoiceIO(this)
        brain = Brain(this)
        listenWake()
    }

    private fun isWake(t: String): Boolean {
        val s = t.lowercase()
        return s.contains("हे यार") || s.contains("हेय यार") || s.contains("hey yaar") ||
            s.contains("hey yar") || s.contains("हुमाले")
    }

    private fun restart(delay: Long = 600) {
        if (!alive) return
        main.postDelayed({ if (alive && !busy) listenWake() }, delay)
    }

    private fun listenWake() {
        voice.listen("ne-NP",
            onPartial = { if (!busy && isWake(it)) onWake() },
            onFinal = { if (!busy && isWake(it)) onWake() else restart(300) },
            onError = { restart() })
    }

    private fun onWake() {
        if (busy) return
        busy = true
        voice.stopListening()
        voice.speak("हजुर?") {
            voice.listen("ne-NP",
                onPartial = {},
                onFinal = { cmd ->
                    scope.launch {
                        val reply = brain.ask(cmd)
                        voice.speak(reply) { busy = false; restart(300) }
                    }
                },
                onError = { busy = false; restart() })
        }
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        voice.shutdown()
        super.onDestroy()
    }
}
