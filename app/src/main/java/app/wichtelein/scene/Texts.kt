package app.wichtelein.scene

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.random.Random

/** Sätze des Wichtels. {kind} = Name des Kindes, {wichtel} = Name des Wichtels. */
object Lines {
    val greet = listOf(
        "Hallo {kind}! Ich bin {wichtel}, dein Wichtel!",
        "Huhu {kind}! Schön, dass du da bist!",
        "Psst, {kind}! Ich bin {wichtel} und wohne heimlich in deinem Zimmer.",
    )
    val tidy = listOf(
        "Ordnung muss sein, {kind}!",
        "Ich räume ein bisschen auf!",
        "Schwuppdiwupp, alles wird sauber!",
        "Ein Wichtel hilft gern.",
    )
    val tidyDone = listOf(
        "So, schön aufgeräumt!",
        "Fertig! Mama und Papa freuen sich bestimmt.",
        "Alles blitzeblank, {kind}!",
    )
    val chaos = listOf(
        "Hihi, jetzt gibt es Chaos!",
        "Upsi! Das war ich nicht... doch!",
        "{kind}, nicht verraten!",
        "Wichtelstreich!",
    )
    val dance = listOf(
        "Tanz, tanz, tanz!",
        "Wichtel-Party, {kind}!",
        "Wer tanzt mit mir?",
    )
    val tumble = listOf(
        "Hui, ein Purzelbaum!",
        "Aaaah! Hoppla!",
        "Schau mal, {kind}, ich kann rollen!",
    )
    val dizzy = listOf(
        "Mir ist ganz schwindelig!",
        "Puh, alles dreht sich.",
    )
    val wave = listOf(
        "Hallo {kind}! Ich sehe dich!",
        "Winke, winke!",
        "Hier bin ich, {kind}!",
    )
    val sleep = listOf(
        "Ich bin so müde... gute Nacht.",
        "Gähn... ein kleines Nickerchen.",
    )
    val wake = listOf(
        "Huch, ich bin eingeschlafen!",
        "Oh, schon Morgen?",
    )
    val snack = listOf(
        "Mmmh, ein Keks!",
        "Nur ein winziges Stück, {kind}.",
        "Plätzchen sind das Beste!",
    )
    val hide = listOf(
        "Findest du mich?",
        "Ich verstecke mich, {kind}!",
    )
    val hideFound = listOf(
        "Buh! Hier bin ich!",
        "Hast du mich gesehen, {kind}?",
    )
    val doorHome = listOf(
        "Ich gehe kurz nach Hause, {name}.",
        "Mein Haus ruft mich!",
    )
    val doorExit = listOf(
        "Da bin ich wieder!",
        "Zuhause ist es am schönsten, aber draußen ist es lustig.",
    )
    val doorKnock = listOf(
        "Klopf, klopf! Ist jemand zu Hause?",
        "Klopf, klopf, klopf!",
    )
    val doorWelcome = listOf(
        "Herzlich willkommen im Wichtelhaus!",
        "Meine Tür steht immer offen, {name}.",
    )
    val sit = listOf(
        "Puh, kurz hinsetzen.",
        "So, jetzt gemütlich.",
        "Ein Wichtel braucht auch mal eine Pause.",
    )
    val trip = listOf(
        "Huch! Wer hat das da hingelegt?",
        "Hoppla, ich bin gestolpert!",
        "Aua, mein Wichtelzeh!",
    )
    val tripEmbarrassed = listOf(
        "Das hat keiner gesehen, oder?",
        "Ich wollte das so machen.",
        "Alles gut, {name}, ich bin ein Profi.",
    )
    val gift = listOf(
        "Ich habe etwas für dich, {name}!",
        "Psst, ein Geschenk ist unterwegs.",
    )
    val giftPlaced = listOf(
        "Das ist für dich, {name}. Nicht schütteln!",
        "Überraschung! Mach es später auf.",
    )
    val giftPop = listOf(
        "Simsalabim! Ein Geschenk!",
        "Schau mal, {name}, was da plötzlich liegt!",
    )
    val snowOn = listOf(
        "Oh, schaut mal nach oben!",
        "Ich glaube, es fängt an zu schneien!",
    )
    val snowLike = listOf(
        "Schneeflöckchen, Weißröckchen!",
        "Wie schön, alles wird ganz weiß!",
    )
    val snowOff = listOf(
        "Genug Schnee für heute!",
        "Danke, liebe Wolken!",
    )
    val magic = listOf(
        "Hokuspokus Wichtelzipfel!",
        "Abrakadabra, Spielzeug komm herbei!",
    )
    val surprised = listOf(
        "Ups, das war ich!",
        "Huch, das hat ja geklappt!",
        "Ich wusste gar nicht, dass ich das kann!",
    )
    val bow = listOf(
        "Vielen Dank, vielen Dank!",
        "Applaus, Applaus, {name}!",
    )
    val sneaky = listOf(
        "Pssst, ich nasche nur ganz heimlich.",
        "Der Keks ist doch gar nicht für dich, {name}... oder?",
    )

    /** Allgemeine Wichtelsprüche für zwischendurch. {name} = Name des Kindes, {wichtel} = Name des Wichtels. */
    val general = listOf(
        "Hallo {name}! Heute habe ich wieder etwas angestellt!",
        "Psst, {name}! Nicht verraten, dass ich hier war.",
        "{name}, du bist der beste Freund, den ein Wichtel haben kann!",
        "Wichtel müssen nie ins Bett, aber ich bin trotzdem müde.",
        "Weißt du, {name}, ich sehe alles, was du Gutes tust!",
        "Im Wichtelwald schmecken Kekse nach Wolken.",
        "Ich habe heute Nacht im Zimmer aufgeräumt. Fast alles.",
        "Hihi, {name}, kannst du mich noch sehen?",
        "Mein Name ist {wichtel} und ich liebe Plätzchen!",
        "Wichtel können sich ganz klein machen. Wusstest du das, {name}?",
        "Heute war ich ein bisschen frech. Nur ein ganz kleines bisschen.",
        "{name}, hast du schon deine Zähne geputzt? Ich habe nachgeschaut!",
        "Ein Wichtel hilft gern, aber manchmal stolpert er auch.",
        "Ich habe einen Schatz im Zimmer versteckt. Rate mal, wo!",
        "Du bist mutig, {name}. Das mag ich an dir.",
        "Brrr, der Boden ist kalt an meinen Wichtelfüßen.",
        "Jeden Abend schaue ich, ob du gut schläfst.",
        "Ich kann pfeifen, tanzen und purzeln. Nur nicht gleichzeitig!",
        "{name}, ich wollte die Sterne zählen, aber dann bin ich eingeschlafen.",
        "Wenn du lachst, wird mein Wichtelherz ganz warm.",
        "Oh, ein Krümel! Der gehört mir.",
        "Ich habe ein Geheimnis, {name}. Es ist ein Keks.",
        "Hast du heute etwas Nettes gemacht? Dann bekommt der Wichtel einen Stern.",
        "Alle Wichtel der Welt winken dir zu, {name}!",
        "Manchmal verstecke ich mich hinter der Gardine und lache leise.",
        "Meine Mütze ist die schönste weit und breit, findest du nicht?",
        "Heute Nacht hat es im Wichtelwald geschneit.",
        "Ich bin klein, aber ich habe große Ideen!",
        "{name}, du darfst mir gern einen Wunsch verraten. Aber leise!",
        "Aufräumen macht mit Musik am meisten Spaß.",
        "Ich glaube, die Socke da gehört nicht mir.",
        "Gute Nacht, {name}. Träum was Schönes!",
        "Der Besen und ich sind beste Freunde.",
        "Ein bisschen Chaos gehört zum Wichteln dazu.",
        "{name}, ich habe dich lieb. Ganz doll.",
        "Morgen überrasche ich dich wieder. Aber verrate nichts!",
    )

    val chat = listOf(
        "Weißt du was, {kind}? Du bist toll!",
        "{wichtel} hat dich lieb.",
        "Wann gibt es Plätzchen?",
        "Hast du heute schon gelacht, {kind}?",
        "Ich wette, ich bin schneller als du.",
        "Im Wichtelwald ist es heute sehr still.",
    )
}

/** Ersetzt die Platzhalter in einem Satz. */
fun fill(template: String, kid: String, elf: String): String =
    template.replace("{name}", kid).replace("{kind}", kid).replace("{wichtel}", elf)

/** Wie lange eine Sprechblase sichtbar bleibt. */
fun bubbleDurationMs(text: String): Long = 1800L + text.length * 65L

/** Namen durch Komma, Semikolon oder Zeilenumbruch getrennt. */
fun parseNames(raw: String): List<String> =
    raw.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Eigene Sätze: ein Satz pro Zeile. */
fun parseLines(raw: String): List<String> =
    raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Wählt einen Index nach Gewichten, ohne [exclude] (die letzte Aktion), außer es gibt nichts anderes.
 */
fun pickWeighted(weights: List<Int>, exclude: Int, rnd: Random = Random.Default): Int {
    val w = weights.mapIndexed { i, v -> if (i == exclude) 0 else v.coerceAtLeast(0) }
    val total = w.sum()
    if (total == 0) return weights.indices.firstOrNull { weights[it] > 0 } ?: 0
    var r = rnd.nextInt(total)
    for (i in w.indices) {
        if (r < w[i]) return i
        r -= w[i]
    }
    return 0
}
