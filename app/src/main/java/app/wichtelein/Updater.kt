package app.wichtelein

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Prüft über die öffentliche GitHub-Releases-API, ob ein neuerer Build bereitsteht
 * (gleiches Verfahren wie in den anderen Projekten). Bewusst ohne Token: Ein eingebetteter
 * Token wäre aus der APK auslesbar, deshalb muss das Repository öffentlich sein.
 */
object Updater {
    private const val API = "https://api.github.com/repos/ProTech1982/Wichtelein/releases/latest"

    data class Result(
        val available: Boolean = false,
        val version: String? = null,
        val notes: String? = null,
        val downloadUrl: String? = null,
        val error: String? = null,
    )

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val installed = installedVersion(context)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Wichtelein")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            if (code != 200) return@withContext Result(error = "GitHub antwortete mit Code $code")
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            parse(json, installed)
        } catch (e: Exception) {
            Result(error = "Keine Verbindung zu GitHub")
        } finally {
            conn?.disconnect()
        }
    }

    /** Wertet die GitHub-Antwort aus; getrennt, damit es ohne Netz testbar ist. */
    fun parse(json: JSONObject, installed: Long): Result {
        val tag = json.optString("tag_name", "")
        val assets = json.optJSONArray("assets")
        var url: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").endsWith(".apk")) {
                    url = a.optString("browser_download_url", "").ifEmpty { null }
                    break
                }
            }
        }
        if (url == null) url = json.optString("html_url", "").ifEmpty { null }
        return Result(
            available = buildNumber(tag) > installed && url != null,
            version = json.optString("name", tag),
            notes = json.optString("body", ""),
            downloadUrl = url,
        )
    }

    /** Aus einem Tag wie "build-17" die Nummer ziehen, sonst -1. */
    fun buildNumber(tag: String?): Long = tag?.filter { it in '0'..'9' }?.toLongOrNull() ?: -1L

    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }
}
