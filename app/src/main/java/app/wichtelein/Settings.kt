package app.wichtelein

import android.content.Context

data class Settings(
    val elfName: String = "Wichtel",
    val kidNames: String = "",
    val speak: Boolean = true,
    /** Wichtel-Effekt 0..1: wie stark die Stimme angehoben wird (0 = natürlich). */
    val effect: Float = 0.15f,
    val voiceId: String = VoiceCatalog.DEFAULT_ID,
    val customLines: String = "",
    val colorIndex: Int = 0,
    val sizeScale: Float = 0.30f,
    val elfVoice: Boolean = true,
    val rate: Float = 1.0f,
    val mood: Int = Mood.NEUTRAL,
) {
    fun kidList(): List<String> = parseNames(kidNames)
    fun customList(): List<String> = parseLines(customLines)
}

object SettingsStore {
    private const val FILE = "wichtelein"

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            elfName = p.getString("elfName", d.elfName) ?: d.elfName,
            kidNames = p.getString("kidNames", d.kidNames) ?: d.kidNames,
            speak = p.getBoolean("speak", d.speak),
            effect = p.getFloat("effect", d.effect),
            voiceId = p.getString("voiceId", d.voiceId) ?: d.voiceId,
            customLines = p.getString("customLines", d.customLines) ?: d.customLines,
            colorIndex = p.getInt("colorIndex", d.colorIndex),
            sizeScale = p.getFloat("sizeScale", d.sizeScale),
            elfVoice = p.getBoolean("elfVoice", d.elfVoice),
            rate = p.getFloat("rate", d.rate),
            mood = p.getInt("moodV2", d.mood),
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("elfName", s.elfName)
            .putString("kidNames", s.kidNames)
            .putBoolean("speak", s.speak)
            .putFloat("effect", s.effect)
            .putString("voiceId", s.voiceId)
            .putString("customLines", s.customLines)
            .putInt("colorIndex", s.colorIndex)
            .putFloat("sizeScale", s.sizeScale)
            .putBoolean("elfVoice", s.elfVoice)
            .putFloat("rate", s.rate)
            .putInt("moodV2", s.mood)
            .apply()
    }
}
