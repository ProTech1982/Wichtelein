package app.wichtelein.speech

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Eine auswählbare Offline-Stimme (Piper-Modelle von k2-fsa/sherpa-onnx, Prüfsumme fest eingetragen). */
class VoiceInfo(
    val id: String,
    val name: String,
    val note: String,
    val sizeMb: Int,
    val file: String?, // Name des Pakets bei k2-fsa; null = in der App eingebaut
    val sha256: String?,
    val moods: Boolean = false,
    /** weibliche Stimme (sonst männlich); der Wichtel bekommt nur männliche, die Elfin nur weibliche Stimmen */
    val female: Boolean = false,
    /** passt für Wichtel und Elfin (wählbar in beiden Listen) */
    val both: Boolean = false,
) {
    val bundled get() = file == null
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$file"
}

object VoiceCatalog {
    const val DEFAULT_ID = "thorsten_emotional"
    const val DEFAULT_ELFIN_ID = "ramona"

    val all = listOf(
        VoiceInfo(DEFAULT_ID, "Thorsten (mit Stimmungen)", "Eingebaut. Männlich, natürlich, fünf Stimmungen.", 76, null, null, moods = true),
        VoiceInfo(
            "thorsten_high", "Thorsten (hohe Qualität)", "Männlich, sehr natürlich, klarer Klang.", 110,
            "vits-piper-de_DE-thorsten-high.tar.bz2", "dd4ed1b0d42c30a1a4862fc2b243e8044d52b8889c9ff3d1e99e92028888bc4a",
        ),
        VoiceInfo(
            "miro_high", "Miro (hohe Qualität)", "Hohe Qualität, vermutlich männlich – einfach anhören.", 64,
            "vits-piper-de_DE-miro-high.tar.bz2", "8226bb45d3684286981a55b3e0937e373f37673036a7eee27fc51def2d8c926d",
        ),
        VoiceInfo(
            "dii_high", "Dii (hohe Qualität)", "Hohe Qualität – für Wichtel und Elfin wählbar.", 64,
            "vits-piper-de_DE-dii-high.tar.bz2", "64d46d39afb0f53a49871f5415259ffe474f443696148930b34a003612b69110", female = true, both = true,
        ),
        VoiceInfo(
            "ramona", "Ramona", "Weiblich, heller – passt gut zu einem Wichtelkind.", 63,
            "vits-piper-de_DE-ramona-low.tar.bz2", "79f985c10f86c4d58205b519abc488aa65eb713d54665ff729880a02e2625a42", female = true,
        ),
        VoiceInfo(
            "kerstin", "Kerstin", "Weiblich.", 63,
            "vits-piper-de_DE-kerstin-low.tar.bz2", "4bfe121d5f690a3b87acecd7ae1e70b48f4589ce6ae9f45a1809e6eb19fad97b", female = true,
        ),
        VoiceInfo(
            "karlsson", "Karlsson", "Männlich.", 63,
            "vits-piper-de_DE-karlsson-low.tar.bz2", "5ba231af059746ac2052c9b7d7eaf116e07003dc6948066ab4f40951dca260af",
        ),
    )

    /** Stimmen für den Wichtel (männlich) bzw. die Elfin (weiblich) */
    fun forActor(actor: Int): List<VoiceInfo> = all.filter { it.both || it.female == (actor == 1) }

    fun find(id: String): VoiceInfo = all.firstOrNull { it.id == id } ?: all.first()

    fun voicesDir(context: Context) = File(context.filesDir, "voices")

    /** Ordner der heruntergeladenen Stimme (model.onnx + tokens.txt). */
    fun dir(context: Context, id: String) = File(voicesDir(context), id)

    fun isInstalled(context: Context, id: String): Boolean {
        val v = find(id)
        return v.bundled || File(dir(context, id), ".ready").exists()
    }
}

class DownloadState(val progress: Float, val error: String? = null, val done: Boolean = false)

/** Lädt zusätzliche Stimmen von der offiziellen k2-fsa-Veröffentlichung, prüft SHA-256 und entpackt Modell + Tokens. */
object VoiceDownloads {
    val states = mutableStateMapOf<String, DownloadState>()

    suspend fun download(context: Context, v: VoiceInfo) {
        if (v.bundled || states[v.id]?.let { !it.done && it.error == null } == true) return
        states[v.id] = DownloadState(0f)
        try {
            withContext(Dispatchers.IO) { doDownload(context.applicationContext, v) }
            states[v.id] = DownloadState(1f, done = true)
        } catch (t: Throwable) {
            states[v.id] = DownloadState(0f, error = t.message ?: "Download fehlgeschlagen")
        }
    }

    private fun doDownload(context: Context, v: VoiceInfo) {
        val tmp = File(context.cacheDir, "voice-${v.id}.tar.bz2")
        try {
            val conn = URL(v.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) throw IllegalStateException("Server antwortete mit Code ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: (v.sizeMb * 1024L * 1024L)
            val md = MessageDigest.getInstance("SHA-256")
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        read += n
                        states[v.id] = DownloadState((read.toFloat() / total).coerceAtMost(0.97f))
                    }
                }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it) }
            if (sha != v.sha256) throw IllegalStateException("Prüfsumme stimmt nicht – Download verworfen")

            val dest = VoiceCatalog.dir(context, v.id)
            dest.deleteRecursively()
            dest.mkdirs()
            var gotModel = false
            var gotTokens = false
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(tmp)))).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    if (e.isDirectory) continue
                    val target = when {
                        e.name.endsWith(".onnx") -> File(dest, "model.onnx").also { gotModel = true }
                        e.name.endsWith("/tokens.txt") -> File(dest, "tokens.txt").also { gotTokens = true }
                        else -> null
                    } ?: continue
                    target.outputStream().use { tar.copyTo(it) }
                }
            }
            if (!gotModel || !gotTokens) throw IllegalStateException("Paket unvollständig")
            File(dest, ".ready").writeText("ok")
        } finally {
            tmp.delete()
        }
    }
}
