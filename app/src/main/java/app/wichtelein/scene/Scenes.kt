package app.wichtelein.scene

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.random.Random

enum class Category(val label: String, val emoji: String) {
    BRAV("Braver Wichtel", "😇"),
    FRECH("Frecher Wichtel", "😜"),
    UEBERRASCHUNG("Überraschung", "🎁"),
}

/** Eine kleine Geschichte des Wichtels. [run] spielt sie ab und geht dabei die Wegpunkte ab. */
class SceneDef(
    val id: String,
    val name: String,
    val category: Category,
    val description: String,
    /** braucht die Tür des Wichtelhauses (im Editor markiert) */
    val needsDoor: Boolean = false,
    /** braucht ein Bett im Bild (im Editor unter „Objekte“ aufgestellt) */
    val needsBed: Boolean = false,
    val run: suspend ElfEngine.(Stops) -> Unit,
)

object Scenes {
    val all: List<SceneDef> = listOf(
        // ---- Braver Wichtel
        SceneDef("brav_fegen", "Zimmer fegen", Category.BRAV, "Der Wichtel fegt den Boden und ist stolz darauf.") { s ->
            walkTo(s.next())
            doSweep()
            sayFrom(Lines.tidyDone)
        },
        SceneDef("brav_spielzeug", "Spielzeug einsammeln", Category.BRAV, "Verstreutes Spielzeug wird aufgesammelt und der Boden gefegt.") { _ ->
            spawnMess(4)
            doTidy()
        },
        SceneDef("brav_geschenk", "Geschenk bringen", Category.BRAV, "Er trägt ein Geschenk herbei und stellt es ab.") { s ->
            doGiftCarry(s.next())
            doWave()
        },
        SceneDef("brav_gutenacht", "Gute Nacht", Category.BRAV, "Der Wichtel setzt sich, winkt und schläft ein.") { s ->
            walkTo(s.next())
            doSit(1500)
            doWave()
            doSleep()
        },
        SceneDef("brav_schnee", "Es schneit", Category.BRAV, "Der Wichtel schaut nach oben, es fängt an zu schneien und er freut sich.") { _ ->
            doSnowOn()
            idle(4000)
            doWave()
        },
        SceneDef("brav_bett", "Ins Bett und schnarchen", Category.BRAV, "Der Wichtel legt sich in sein Bett und schnarcht laut.", needsBed = true) { _ ->
            doBed(findBed())
        },
        SceneDef("brav_keks", "Keks-Pause", Category.BRAV, "Er setzt sich hin und genießt einen Keks.") { s ->
            walkTo(s.next())
            doSit(1200)
            doSnack()
        },
        SceneDef("brav_heim", "Nach Hause gehen", Category.BRAV, "Er öffnet die Tür seines Wichtelhauses, schaut in die weihnachtliche Stube, dreht sich um und schließt sie.", needsDoor = true) { _ ->
            doEnterHouse()
            doWave()
        },
        SceneDef("brav_zuhause", "Ins Haus und drinnen bleiben", Category.BRAV, "Er geht in sein Wichtelhaus, schließt die Tür von innen und kommt nach einer Weile wieder heraus.", needsDoor = true) { _ ->
            doGoInside()
            idle(3500)
            doComeOutside()
            doWave()
        },
        SceneDef("brav_tuer_fegen", "Vor der Tür fegen", Category.BRAV, "Er fegt vor seinem Haus und öffnet die Tür zur Begrüßung.", needsDoor = true) { _ ->
            doDoorSweep()
        },
        // ---- Frecher Wichtel
        SceneDef("frech_tuer", "Tür-Streich", Category.FRECH, "Klopf, klopf – er reißt die Tür auf, späht hinein, knallt sie zu und lacht.", needsDoor = true) { _ ->
            doEnterHouse(fast = true)
            sayFrom(Lines.chaos)
        },
        SceneDef("frech_chaos", "Chaos-Wichtel", Category.FRECH, "Er wirft Spielzeug durch das Zimmer.") { s ->
            walkTo(s.next())
            doChaos()
        },
        SceneDef("frech_stolpern", "Stolperfalle", Category.FRECH, "Er rennt los, stolpert und tut so, als wäre nichts.") { s ->
            walkTo(s.next(), 0.5f)
            doTrip()
        },
        SceneDef("frech_keksdieb", "Keksdieb", Category.FRECH, "Heimlich nascht er und versteckt sich dann.") { s ->
            walkTo(s.next())
            doSnack(sneaky = true)
            doHide()
        },
        SceneDef("frech_verstecken", "Versteckspiel", Category.FRECH, "Puff – weg ist er, und er taucht woanders wieder auf.") { s ->
            doHide()
            walkTo(s.next())
            doHide()
        },
        SceneDef("frech_besen", "Fegen mit Folgen", Category.FRECH, "Erst wird gefegt, dann doch lieber alles durcheinandergebracht.") { s ->
            walkTo(s.next())
            doSweep(2000)
            doChaos()
        },
        // ---- Überraschung
        SceneDef("ueb_haus_geschenk", "Geschenk aus dem Haus", Category.UEBERRASCHUNG, "Die Tür geht auf, und er holt ein Geschenk aus dem Haus.", needsDoor = true) { s ->
            doExitHouse(carryGift = true)
            doGiftCarry(s.next())
        },
        SceneDef("ueb_geschenk", "Geschenk-Zauber", Category.UEBERRASCHUNG, "Plötzlich liegt ein Geschenk da – mit Konfetti.") { s ->
            walkTo(s.next())
            doGiftPop()
        },
        SceneDef("ueb_tanzparty", "Tanzparty", Category.UEBERRASCHUNG, "Der Wichtel tanzt und Konfetti regnet.") { s ->
            walkTo(s.next())
            doDance(withConfetti = true)
        },
        SceneDef("ueb_purzel", "Purzelbaum-Show", Category.UEBERRASCHUNG, "Zwei Purzelbäume und eine tiefe Verbeugung.") { s ->
            walkTo(s.next())
            doTumble()
            doTumble()
            doBow()
        },
        SceneDef("ueb_zauber", "Zauberspruch", Category.UEBERRASCHUNG, "Mit einem Zauberspruch erscheint Spielzeug aus dem Nichts.") { s ->
            walkTo(s.next())
            doMagic()
        },
        SceneDef("ueb_schlafwandler", "Schlafwandler", Category.UEBERRASCHUNG, "Er schläft ein, wacht erschrocken auf und tanzt los.") { _ ->
            doSleep()
            doDance()
        },
    )

    fun find(id: String): SceneDef? = all.firstOrNull { it.id == id }

    fun inCategory(c: Category) = all.filter { it.category == c }

    /**
     * Wählt eine Szene. [category] = null mischt alle Kategorien. Nur erlaubte Szenen ([enabled] = null: alle);
     * die zuletzt gespielte wird vermieden.
     */
    fun pick(category: Category?, enabled: Set<String>?, last: SceneDef?, rng: Random, hasDoor: Boolean = false, hasBed: Boolean = false): SceneDef {
        val cat = category ?: Category.values().random(rng)
        val allowed = all.filter { (enabled == null || it.id in enabled) && (hasDoor || !it.needsDoor) && (hasBed || !it.needsBed) }
        var pool = allowed.filter { it.category == cat && it != last }
        if (pool.isEmpty()) pool = allowed.filter { it != last }
        if (pool.isEmpty()) pool = allowed // nur eine erlaubte Szene: sie darf sich wiederholen
        if (pool.isEmpty()) pool = all // alles abgeschaltet: lieber etwas spielen als nichts
        return pool.random(rng)
    }
}
