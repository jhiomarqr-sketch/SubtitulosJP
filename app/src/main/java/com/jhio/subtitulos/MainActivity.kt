package com.jhio.subtitulos

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var downloading = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (!Settings.canDrawOverlays(this)) openOverlaySettings()
            refresh()
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == Activity.RESULT_OK && data != null) {
                val i = Intent(this, CaptionService::class.java)
                    .setAction(CaptionService.ACTION_START)
                    .putExtra(CaptionService.EXTRA_CODE, res.resultCode)
                    .putExtra(CaptionService.EXTRA_DATA, data)
                ContextCompat.startForegroundService(this, i)
                status.text = "✅ Subtítulos activos. Abre tu video (YouTube, etc.)."
            } else {
                status.text = "Cancelaste el permiso de captura."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)

        findViewById<Button>(R.id.btnPerms).setOnClickListener { askPermissions() }
        findViewById<Button>(R.id.btnModels).setOnClickListener { downloadModels() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startCaptions() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, CaptionService::class.java))
            status.text = "Detenido."
        }
    }

    override fun onResume() {
        super.onResume()
        if (!downloading) refresh()
    }

    private fun missing(): List<String> {
        val m = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) m += "permiso de audio"
        if (!Settings.canDrawOverlays(this)) m += "mostrar sobre otras apps"
        if (!ModelManager.isTranslatorReady(this)) m += "modelo de traducción"
        if (!ModelManager.isVoskReady(this)) m += "modelo de voz japonés"
        return m
    }

    private fun refresh() {
        val m = missing()
        status.text = if (m.isEmpty()) "Todo listo. Pulsa «3. Iniciar subtítulos»."
        else "Falta: " + m.joinToString(", ")
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }

    private fun openOverlaySettings() {
        status.text = "Activa «Mostrar sobre otras apps» para Subtítulos JP→ES y vuelve."
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun downloadModels() {
        if (downloading) return
        downloading = true
        progress.visibility = View.VISIBLE
        progress.progress = 0
        if (ModelManager.isTranslatorReady(this)) {
            downloadVoice()
        } else {
            status.text = "Descargando modelo de traducción… (puede tardar un poco)"
            ModelManager.downloadTranslator(this) { ok, err ->
                if (ok) downloadVoice() else finishDownload("Error en traducción: $err")
            }
        }
    }

    private fun downloadVoice() {
        if (ModelManager.isVoskReady(this)) { finishDownload(null); return }
        status.text = "Descargando modelo de voz japonés…"
        Thread {
            try {
                ModelManager.downloadVosk(this) { pct ->
                    runOnUiThread {
                        progress.progress = pct
                        status.text = if (pct < 100) "Modelo de voz: $pct%" else "Descomprimiendo…"
                    }
                }
                runOnUiThread { finishDownload(null) }
            } catch (e: Exception) {
                runOnUiThread { finishDownload("Error en modelo de voz: ${e.message}") }
            }
        }.start()
    }

    private fun finishDownload(error: String?) {
        downloading = false
        progress.visibility = View.GONE
        if (error != null) status.text = "$error\nRevisa tu conexión e inténtalo otra vez."
        else refresh()
    }

    private fun startCaptions() {
        val m = missing()
        if (m.isNotEmpty()) {
            status.text = "Antes completa: " + m.joinToString(", ")
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            // Fuerza "pantalla completa" para que se capture el audio de cualquier app
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }
}
