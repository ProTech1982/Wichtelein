package app.wichtelein

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Schreibt den letzten Absturz in eine Datei (`absturz.txt`), damit man ihn in den Einstellungen ansehen und weitergeben kann.
 * Alles bleibt auf dem Gerät.
 */
object CrashLog {
    private const val FILE = "absturz.txt"
    private const val MAX = 6000

    /** Text eines Absturzberichts. */
    fun format(thread: String, e: Throwable, time: Long = System.currentTimeMillis(), maxMemMb: Long = Runtime.getRuntime().maxMemory() / 1_048_576L): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        val head = "Absturz am ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date(time))}\n" +
            "Thread: $thread · Speicherlimit: $maxMemMb MB\n\n"
        return (head + trace).take(MAX)
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(app.filesDir, FILE).writeText(format(t.name, e))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun last(context: Context): String? = try {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }
}
