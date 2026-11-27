package com.jhio.subtitulos

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.mlkit.nl.translate.Translator
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicLong

/**
 * Servicio en primer plano:
 * audio de otras apps (MediaProjection) → Vosk (japonés a texto) → ML Kit (a español) → burbuja.
 */
class CaptionService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "subtitulos"
        private const val NOTIF_ID = 1
        private const val SAMPLE_RATE = 16000
        private const val PARTIAL_INTERVAL_MS = 900L
        private const val CLEAR_AFTER_MS = 6000L
        private const val TAG = "CaptionService"
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var worker: Thread? = null
    private var overlay: CaptionOverlay? = null
    private var translator: Translator? = null

    @Volatile private var active = false
    private val seq = AtomicLong(0)
    private var shownSeq = 0L               // solo se usa en el hilo principal
    private var lastPartial = ""
    private var lastPartialAt = 0L

    private val clearRunnable = Runnable { overlay?.setText("", "") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || active) return START_NOT_STICKY

        // Android 14+: hay que pasar a primer plano ANTES de pedir la MediaProjection
        startInForeground()

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = data?.let { mpm.getMediaProjection(code, it) }
        if (proj == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopSelf() } }
        }, main)
        projection = proj

        overlay = CaptionOverlay(this).also { it.show() }
        translator = ModelManager.newTranslator()

        try {
            startCapture(proj)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la captura", e)
            overlay?.setText("", "Error al capturar audio: ${e.message}")
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Subtítulos en vivo", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Subtítulos JP→ES activos")
            .setContentText("Escuchando el audio del celular")
            .setOngoing(true)
            .addAction(0, "Detener", stopIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    @SuppressLint("MissingPermission") // el permiso de micrófono se verifica en MainActivity
    private fun startCapture(proj: MediaProjection) {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf * 2, SAMPLE_RATE))
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        active = true
        worker = Thread({ recognitionLoop(record) }, "reconocimiento").also { it.start() }
    }

    private fun recognitionLoop(record: AudioRecord) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val model = try {
            Model(ModelManager.voskModelDir(this).absolutePath)
        } catch (e: Exception) {
            main.post { overlay?.setText("", "Error cargando el modelo de voz: ${e.message}") }
            record.release()
            return
        }
        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        val buffer = ShortArray(SAMPLE_RATE / 10) // bloques de 100 ms

        main.post { overlay?.setText("", "Escuchando… reproduce tu video") }
        try {
            record.startRecording()
            while (active) {
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) { SystemClock.sleep(20); continue }
                if (n == 0) continue
                if (recognizer.acceptWaveForm(buffer, n)) {
                    val text = clean(JSONObject(recognizer.result).optString("text"))
                    if (text.isNotEmpty()) translateAndShow(text, isFinal = true)
                    lastPartial = ""
                } else {
                    val partial = clean(JSONObject(recognizer.partialResult).optString("partial"))
                    val now = SystemClock.elapsedRealtime()
                    if (partial.length >= 2 && partial != lastPartial &&
                        now - lastPartialAt >= PARTIAL_INTERVAL_MS
                    ) {
                        lastPartial = partial
                        lastPartialAt = now
                        translateAndShow(partial, isFinal = false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en reconocimiento", e)
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
            recognizer.close()
            model.close()
        }
    }

    /** Vosk separa las palabras japonesas con espacios; se quitan para traducir mejor. */
    private fun clean(s: String) = s.replace(" ", "").trim()

    private fun translateAndShow(ja: String, isFinal: Boolean) {
        val id = seq.incrementAndGet()
        val t = translator ?: return
        t.translate(ja)
            .addOnSuccessListener { es ->
                // Evita que una traducción vieja tape a una más nueva
                if (id < shownSeq) return@addOnSuccessListener
                shownSeq = id
                overlay?.setText(ja, if (isFinal) es else "$es…")
                main.removeCallbacks(clearRunnable)
                main.postDelayed(clearRunnable, CLEAR_AFTER_MS)
            }
            .addOnFailureListener { e -> Log.w(TAG, "Fallo de traducción", e) }
    }

    override fun onDestroy() {
        active = false
        worker?.join(1000)
        worker = null
        main.removeCallbacksAndMessages(null)
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        overlay?.hide()
        overlay = null
        translator?.close()
        translator = null
        super.onDestroy()
    }
}
