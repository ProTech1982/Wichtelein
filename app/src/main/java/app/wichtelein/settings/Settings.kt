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
    /** Standard-Sprechblase (Anzeigen, Form, Größe); einzelne Aktionen können eine eigene haben */
    val bubble: app.wichtelein.anim.BubbleSpec = app.wichtelein.anim.BubbleSpec(),
    /** Hintergrundmusik: 0 = aus, 1..4 = eingebaute Melodie, 5 = eigene Datei */
    val music: Int = 0,
    val musicVolume: Float = 0.35f,
    /** Anzeigename der eigenen Musikdatei */
    val musicName: String = "",
    /** Summen und Brummen: statt der Stimme (Stimme aus) oder zusätzlich (kleine Weihnachtsmelodien in Pausen) */
    val hum: Boolean = false,
    /** Farbe und Licht je Art von Gegenstand (Schlüssel = SceneryType.id) */
    val sceneryStyles: Map<String, app.wichtelein.anim.SceneryStyle> = emptyMap(),
    /** Perspektive im Bild: 1 = weiter oben kleiner, 0 = überall gleich groß */
    val perspective: Float = 1f,
    /** 3D-Wichtel (in Blender gerendert, 360° drehbar) statt der 2D-Figur */
    val figure3d: Boolean = false,
    /** Wer erscheint: 0 = Wichtel, 1 = Wichtelfrau (Elfin), 2 = beide zusammen */
    val cast: Int = 0,
    /** Kleiderfarben der Elfin (Nummern wie beim Wichtel) und ihre eigenen Sätze */
    val elfinHatColor: Int = 0,
    val elfinJacketColor: Int = 0,
    val elfinLegColor: Int = 0,
    val elfinBootColor: Int = 0,
    val elfinCustomLines: String = "",
    /** Name der Elfin ({wichtel} in ihren Sätzen) */
    val elfinName: String = "Elfi",
    /** Stimme der Elfin (nur weibliche Stimmen); die Stimme des Wichtels ist [voiceId] (nur männliche) */
    val elfinVoiceId: String = VoiceCatalog.DEFAULT_ELFIN_ID,
    /** Weihnachtsgeräusche (Glöckchen, Tür, Besen, …) */
    val soundFx: Boolean = true,
    /** Abgeschaltete Szenen (Wichtelaktionen). */
    val disabledScenes: Set<String> = emptySet(),
    /** Aussehen der App: 0 = wie das Handy (System), 1 = hell, 2 = dunkel */
    val themeMode: Int = 0,
) {
    /** nur die Elfin tritt auf */
    val elfin: Boolean get() = cast == 1

    /** Wichtel und Elfin treten zusammen auf */
    val both: Boolean get() = cast == 2

    /** Stimme der Figur [actor] (0 = Wichtel, 1 = Elfin) */
    fun voiceFor(actor: Int): String = if (actor == 1) elfinVoiceId else voiceId

    fun nameFor(actor: Int): String = if (actor == 1) elfinName else elfName

    /** Einstellungen für die Sprachausgabe der Figur [actor] (die Stimme der Figur steht dann in [voiceId]) */
    fun forActor(actor: Int): Settings = copy(voiceId = voiceFor(actor))

    fun colors() = ElfColors(hatColor, jacketColor, legColor, bootColor).clamped()

    /** Kleiderfarben der Figur [actor] (0 = Wichtel, 1 = Elfin) */
    fun colorsFor(actor: Int) = if (actor == 1) ElfColors(elfinHatColor, elfinJacketColor, elfinLegColor, elfinBootColor).clamped() else colors()

    fun activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    /** Namen des aktiven Profils (für {name} in den Sprüchen). */
    fun kidList(): List<String> = activeProfile()?.let { parseNames(it.name) } ?: emptyList()

    fun customList(): List<String> = parseLines(customLines)

    /** Eigene Sätze der Figur [actor] (die Elfin benutzt nur ihre eigenen) */
    fun customListFor(actor: Int): List<String> = parseLines(if (actor == 1) elfinCustomLines else customLines)

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
            cast = if (p.contains("cast")) p.getInt("cast", 0).coerceIn(0, 2) else if (p.getBoolean("elfin", false)) 1 else 0,
            elfinName = p.getString("elfinName", d.elfinName) ?: d.elfinName,
            elfinHatColor = p.getInt("elfinHatColor", 0),
            elfinJacketColor = p.getInt("elfinJacketColor", 0),
            elfinLegColor = p.getInt("elfinLegColor", 0),
            elfinBootColor = p.getInt("elfinBootColor", 0),
            elfinCustomLines = p.getString("elfinCustomLines", "") ?: "",
            elfinVoiceId = p.getString("elfinVoiceId", d.elfinVoiceId) ?: d.elfinVoiceId,
            perspective = p.getFloat("perspective", d.perspective),
            bubble = app.wichtelein.anim.BubbleSpec(
                show = p.getBoolean("bubbleShow", true),
                shape = app.wichtelein.anim.BubbleShape.values().getOrElse(p.getInt("bubbleShape", 0)) { app.wichtelein.anim.BubbleShape.ROUNDED },
                size = p.getFloat("bubbleSize", 1f).coerceIn(app.wichtelein.anim.BubbleSpec.MIN_SIZE, app.wichtelein.anim.BubbleSpec.MAX_SIZE),
            ),
            hum = p.getBoolean("hum", d.hum),
            music = p.getInt("music", d.music),
            musicVolume = p.getFloat("musicVolume", d.musicVolume).coerceIn(0f, 1f),
            musicName = p.getString("musicName", "") ?: "",
            sceneryStyles = app.wichtelein.anim.SceneryPalette.decode(p.getString("sceneryStyles", "")),
            disabledScenes = (p.getStringSet("disabledScenes", emptySet()) ?: emptySet()).toSet(),
            themeMode = p.getInt("themeMode", d.themeMode).coerceIn(0, 2),
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
            .putInt("cast", s.cast)
            .putString("elfinName", s.elfinName)
            .putInt("elfinHatColor", s.elfinHatColor)
            .putInt("elfinJacketColor", s.elfinJacketColor)
            .putInt("elfinLegColor", s.elfinLegColor)
            .putInt("elfinBootColor", s.elfinBootColor)
            .putString("elfinCustomLines", s.elfinCustomLines)
            .putString("elfinVoiceId", s.elfinVoiceId)
            .putFloat("perspective", s.perspective)
            .putBoolean("bubbleShow", s.bubble.show)
            .putInt("bubbleShape", s.bubble.shape.ordinal)
            .putFloat("bubbleSize", s.bubble.size)
            .putBoolean("hum", s.hum)
            .putInt("music", s.music)
            .putFloat("musicVolume", s.musicVolume)
            .putString("musicName", s.musicName)
            .putString("sceneryStyles", app.wichtelein.anim.SceneryPalette.encode(s.sceneryStyles))
            .putStringSet("disabledScenes", s.disabledScenes)
            .putInt("themeMode", s.themeMode)
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
