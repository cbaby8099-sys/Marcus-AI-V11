package com.marcusai.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Experimental phrase listener. It uses Android's speech recognition service,
 * not a dedicated low-power wake-word model. It must be started by the user
 * while Marcus is visible and is always represented by an ongoing notification.
 */
class VoiceWakeService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var awaitingCommand = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        startRecognition()
        return START_STICKY
    }

    private fun startRecognition() {
        if (restarting || !SpeechRecognizer.isRecognitionAvailable(this)) return
        restarting = true
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { restarting = false }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    restarting = false
                    if (error != SpeechRecognizer.ERROR_CLIENT) restartSoon()
                }
                override fun onResults(results: android.os.Bundle?) {
                    restarting = false
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    val clean = phrase.lowercase(Locale.US).trim()
                    if (awaitingCommand && clean.isNotBlank()) {
                        awaitingCommand = false
                        sendBroadcast(Intent(ACTION_COMMAND).setPackage(packageName).putExtra(EXTRA_TEXT, phrase))
                    } else if (clean.contains("hey marcus") || clean.contains("hey, marcus")) {
                        awaitingCommand = true
                        sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                    }
                    restartSoon()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(request)
        } catch (_: Exception) {
            restarting = false
            restartSoon()
        }
    }

    private fun restartSoon() {
        android.os.Handler(mainLooper).postDelayed({ startRecognition() }, 900L)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Marcus wake listener", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Visible status while Hey Marcus listening is enabled" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, VoiceWakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Marcus wake listener is active")
            .setContentText("Listening for “Hey Marcus”. Tap Stop to turn it off.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopIntent)
            .build()
    }

    override fun onDestroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_WAKE = "com.marcusai.app.MARCUS_WAKE"
        const val ACTION_COMMAND = "com.marcusai.app.MARCUS_COMMAND"
        const val ACTION_STOP = "com.marcusai.app.STOP_WAKE_LISTENER"
        const val EXTRA_TEXT = "spoken_text"
        private const val CHANNEL_ID = "marcus_wake_listener"
        private const val NOTIFICATION_ID = 1601
    }
}
