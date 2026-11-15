package app.wichtelein.scene

/**
 * Aus dem Text der Sprechblase wird der gesprochene Text: Zischlaute und Lautmalereien („Psst“, „Schhh“, „Brrr“, „Pfff“, „Hmmm“)
 * würde die Stimme Buchstabe für Buchstabe vorlesen. Sie werden aus dem Gesprochenen entfernt; statt „Psst“ und „Schhh“ spielt der
 * Wichtel ein Zischgeräusch ([Prepared.hiss]). In der Sprechblase bleibt der Text unverändert.
 */
object SpeechText {
    class Prepared(val text: String, val hiss: Boolean)

    private val hissWord = Regex("""(?i)(?<![\p{L}])(?:p+s+t+|p+s+c+h+t*|s+c?h{2,}|sh+)(?![\p{L}])""")
    private val soundWord = Regex("""(?i)(?<![\p{L}])(?:b+r{2,}|p+f{2,}|h+m{2,}|m{2,}h*|(?:tz){2,}t?|u+h{2,})(?![\p{L}])""")

    fun prepare(text: String): Prepared {
        val hiss = hissWord.containsMatchIn(text)
        var t = hissWord.replace(text, " ")
        t = soundWord.replace(t, " ")
        // Satzzeichen aufräumen, die durch das Entfernen allein stehen
        t = t.replace(Regex("""\s+"""), " ")
        t = t.replace(Regex("""\s+([,.!?…;:])"""), "$1")
        t = t.replace(Regex("""([,;:])(?:\s*[,;:])+"""), "$1")
        t = t.replace(Regex("""^[\s,;:.!?…-]+"""), "")
        t = t.replace(Regex("""\s*[,;:-]+\s*$"""), "")
        return Prepared(t.trim(), hiss)
    }
}
