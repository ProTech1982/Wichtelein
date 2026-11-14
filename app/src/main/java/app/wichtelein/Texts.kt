package app.wichtelein

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
