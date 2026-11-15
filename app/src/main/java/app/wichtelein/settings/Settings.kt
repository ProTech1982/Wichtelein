package app.wichtelein.settings

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.ui.*

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Ein Kinderprofil. [name] darf mehrere Namen enthalten (Geschwister), durch Komma getrennt. */
data class Profile(val id: String, val name: String)

data class Settings(
    val elfName: String = "Wichtel",
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String = "",
    val onboarded: Boolean = false,
    val speak: Boolean = true,
    /** Wichtel-Effekt 0..1: wie stark die Stimme angehoben wird (0 = natürlich). */
    val effect: Float = 0.15f,
    val voiceId: String = VoiceCatalog.DEFAULT_ID,
    val customLines: String = "",
    val hatColor: Int = 0,
    val jacketColor: Int = 0,
    val legColor: Int = 0,
    val bootColor: Int = 0,
    val sizeScale: Float = 0.30f,
    val elfVoice: Boolean = true,
    val rate: Float = 1.0f,
    val mood: Int = Mood.NEUTRAL,
    /** 3D-Wichtel (in Blender gerendert, 360° drehbar) statt der 2D-Figur */
    val figure3d: Boolean = false,
    /** Weihnachtsgeräusche (Glöckchen, Tür, Besen, …) */
    val soundFx: Boolean = true,
    /** Abgeschaltete Szenen (Wichtelaktionen). */
    val disabledScenes: Set<String> = emptySet(),
) {
    fun colors() = ElfColors(hatColor, jacketColor, legColor, bootColor).clamped()

    fun activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    /** Namen des aktiven Profils (für {name} in den Sprüchen). */
    fun kidList(): List<String> = activeProfile()?.let { parseNames(it.name) } ?: emptyList()

    fun customList(): List<String> = parseLines(customLines)

    /** Erlaubte Szenen; null = alle. */
    fun enabledScenes(): Set<String>? =
        if (disabledScenes.isEmpty()) null else Scenes.all.map { it.id }.toSet() - disabledScenes

    fun withProfile(name: String): Settings {
        val p = Profile(UUID.randomUUID().toString(), name.trim())
        return copy(profiles = profiles + p, activeProfileId = p.id)
    }
}

object SettingsStore {
    private const val FILE = "wichtelein"

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        var profiles = parseProfiles(p.getString("profiles", null))
        var active = p.getString("activeProfile", "") ?: ""
        var onboarded = p.getBoolean("onboarded", false)
        // Übernahme von Version 0.1: ein einzelnes Namensfeld wurde zum ersten Profil
        val legacy = p.getString("kidNames", "")?.trim().orEmpty()
        if (profiles.isEmpty() && legacy.isNotEmpty()) {
            val prof = Profile(UUID.randomUUID().toString(), legacy)
            profiles = listOf(prof)
            active = prof.id
            onboarded = true
        }
        // Übernahme von Version 0.1: Farbvarianten 1..3 (Rot, Blau, Lila) sind heute Einträge 1, 5, 6 der Palette
        val legacyColor = when (p.getInt("colorIndex", 0)) { 1 -> 1; 2 -> 5; 3 -> 6; else -> 0 }
        return Settings(
            elfName = p.getString("elfName", d.elfName) ?: d.elfName,
            profiles = profiles,
            activeProfileId = active,
            onboarded = onboarded,
            speak = p.getBoolean("speak", d.speak),
            effect = p.getFloat("effect", d.effect),
            voiceId = p.getString("voiceId", d.voiceId) ?: d.voiceId,
            customLines = p.getString("customLines", d.customLines) ?: d.customLines,
            hatColor = p.getInt("hatColor", legacyColor),
            jacketColor = p.getInt("jacketColor", legacyColor),
            legColor = p.getInt("legColor", legacyColor),
            bootColor = p.getInt("bootColor", d.bootColor),
            sizeScale = p.getFloat("sizeScale", d.sizeScale),
            elfVoice = p.getBoolean("elfVoice", d.elfVoice),
            rate = p.getFloat("rate", d.rate),
            mood = p.getInt("moodV2", d.mood),
            soundFx = p.getBoolean("soundFx", d.soundFx),
            figure3d = p.getBoolean("figure3d", d.figure3d),
            disabledScenes = (p.getStringSet("disabledScenes", emptySet()) ?: emptySet()).toSet(),
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("elfName", s.elfName)
            .putString("profiles", profilesToJson(s.profiles))
            .putString("activeProfile", s.activeProfileId)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("speak", s.speak)
            .putFloat("effect", s.effect)
            .putString("voiceId", s.voiceId)
            .putString("customLines", s.customLines)
            .putInt("hatColor", s.hatColor)
            .putInt("jacketColor", s.jacketColor)
            .putInt("legColor", s.legColor)
            .putInt("bootColor", s.bootColor)
            .putFloat("sizeScale", s.sizeScale)
            .putBoolean("elfVoice", s.elfVoice)
            .putFloat("rate", s.rate)
            .putInt("moodV2", s.mood)
            .putBoolean("soundFx", s.soundFx)
            .putBoolean("figure3d", s.figure3d)
            .putStringSet("disabledScenes", s.disabledScenes)
            .apply()
    }

    fun profilesToJson(list: List<Profile>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        return arr.toString()
    }

    fun parseProfiles(json: String?): List<Profile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Profile(o.getString("id"), o.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
