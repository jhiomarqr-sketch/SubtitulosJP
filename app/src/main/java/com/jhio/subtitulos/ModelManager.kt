package com.jhio.subtitulos

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Descarga y verifica los modelos: Vosk (voz japonesa) y ML Kit (traducción ja→es). */
object ModelManager {

    private const val VOSK_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
    private const val VOSK_DIR = "vosk-model-small-ja-0.22"
    private const val PREFS = "modelos"
    private const val KEY_TRANSLATOR = "translator_ok"

    fun voskModelDir(ctx: Context) = File(ctx.filesDir, VOSK_DIR)

    fun isVoskReady(ctx: Context) = File(voskModelDir(ctx), ".ok").exists()

    fun isTranslatorReady(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TRANSLATOR, false)

    fun newTranslator(): Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    )

    /** Descarga el modelo de traducción. onDone se llama en el hilo principal. */
    fun downloadTranslator(ctx: Context, onDone: (Boolean, String?) -> Unit) {
        val t = newTranslator()
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_TRANSLATOR, true).apply()
                t.close()
                onDone(true, null)
            }
            .addOnFailureListener { e ->
                t.close()
                onDone(false, e.message)
            }
    }

    /** Descarga y descomprime el modelo de voz. Bloqueante: llamar desde un hilo secundario. */
    fun downloadVosk(ctx: Context, onProgress: (Int) -> Unit) {
        val tmp = File(ctx.cacheDir, "vosk.zip")
        val conn = URL(VOSK_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val root = ctx.filesDir.canonicalFile
        voskModelDir(ctx).deleteRecursively()
        ZipInputStream(BufferedInputStream(FileInputStream(tmp))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(root, entry.name).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) {
                    throw IOException("Archivo zip inválido")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        tmp.delete()

        val dir = voskModelDir(ctx)
        if (!dir.isDirectory || dir.list().isNullOrEmpty()) {
            throw IOException("El modelo se descargó incompleto")
        }
        File(dir, ".ok").createNewFile()
    }
}
