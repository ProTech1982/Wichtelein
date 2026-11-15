package app.wichtelein.scene

/** Alle Satzlisten einer Figur (Wichtel: [Lines], Elfin: [ElfinLines]). {kind}/{name} = Name des Kindes, {wichtel} = Name der Figur. */
interface LineBook {
    val greet: List<String>
    val tidy: List<String>
    val tidyDone: List<String>
    val chaos: List<String>
    val dance: List<String>
    val tumble: List<String>
    val dizzy: List<String>
    val wave: List<String>
    val sleep: List<String>
    val wake: List<String>
    val snack: List<String>
    val hide: List<String>
    val hideFound: List<String>
    val doorHome: List<String>
    val doorExit: List<String>
    val doorKnock: List<String>
    val doorWelcome: List<String>
    val sit: List<String>
    val trip: List<String>
    val tripEmbarrassed: List<String>
    val gift: List<String>
    val giftPlaced: List<String>
    val giftPop: List<String>
    val snowOn: List<String>
    val snowLike: List<String>
    val snowOff: List<String>
    val magic: List<String>
    val surprised: List<String>
    val bow: List<String>
    val sneaky: List<String>
    val general: List<String>
    val chat: List<String>
    /** Aufbauen und Ins-Bett-Gehen; {item} = Name des Gegenstands */
    val build: List<String>
    val buildDone: List<String>
    val noBed: List<String>
    val bedNight: List<String>
    val bedMorning: List<String>
}

/** Die Sätze der Elfin: komplett eigene Texte, nichts vom Wichtel. */
object ElfinLines : LineBook {
    override val greet = listOf(
        "Hallo {kind}! Ich bin {wichtel}, deine Elfin!",
        "Hui, {kind}! Schön, dass du da bist!",
        "Psst, {kind}! Ich bin {wichtel}, die Elfin, und wohne heimlich bei dir im Zimmer.",
    )
    override val tidy = listOf(
        "Ein Hauch Elfenstaub, und alles wird ordentlich, {kind}!",
        "Ich kehre ein bisschen, mit Schwung und Tanzschritt!",
        "Husch, husch, der Staub muss weg!",
        "Eine Elfin hilft gern.",
    )
    override val tidyDone = listOf(
        "So, glitzerblank!",
        "Fertig! Das sieht jetzt richtig hübsch aus.",
        "Alles funkelt, {kind}!",
    )
    override val chaos = listOf(
        "Kicher, ein kleiner Elfenstreich!",
        "Oje! Das war nur der Wind. Oder ich.",
        "{kind}, ich verrate nichts, wenn du nichts verrätst!",
        "Elfenstreich!",
    )
    override val dance = listOf(
        "Drehen, drehen, drehen!",
        "Elfen-Party, {kind}!",
        "Wer dreht sich mit mir im Kreis?",
    )
    override val tumble = listOf(
        "Wheee, eine Rolle!",
        "Huch! Mein Zopf ist im Weg!",
        "Schau mal, {kind}, ich kann mich überschlagen!",
    )
    override val dizzy = listOf(
        "Mir dreht sich alles, auch meine Zöpfe!",
        "Puh, der Raum tanzt ohne mich.",
    )
    override val wave = listOf(
        "Hallo {kind}! Ich habe dich entdeckt!",
        "Wink, wink, mit dem ganzen Arm!",
        "Hier drüben, {kind}!",
    )
    override val sleep = listOf(
        "Meine Elfenaugen fallen zu... gute Nacht.",
        "Gähn... ich leg mich kurz auf mein Elfenohr.",
    )
    override val wake = listOf(
        "Huch, ich habe geschlafen!",
        "Schon Morgen? Das ging aber schnell.",
    )
    override val snack = listOf(
        "Mmmh, ein Zuckerplätzchen!",
        "Nur ein winziges Krümelchen, {kind}.",
        "Lebkuchen sind mein Lieblingszauber!",
    )
    override val hide = listOf(
        "Kannst du mich finden?",
        "Ich husche weg, {kind}!",
    )
    override val hideFound = listOf(
        "Kuckuck! Da bin ich!",
        "Na, {kind}, hast du mich entdeckt?",
    )
    override val doorHome = listOf(
        "Ich flitze kurz in mein Elfenhaus, {name}.",
        "Mein Elfenhaus ruft!",
    )
    override val doorExit = listOf(
        "Da bin ich wieder, frisch gekämmt!",
        "Zu Hause ist es gemütlich, aber draußen funkelt es mehr.",
    )
    override val doorKnock = listOf(
        "Klopf, klopf! Ist da jemand?",
        "Tipp, tipp, tipp an die Tür!",
    )
    override val doorWelcome = listOf(
        "Herzlich willkommen in meinem Elfenhaus!",
        "Bei mir ist immer Platz für Gäste, {name}.",
    )
    override val sit = listOf(
        "Puh, ich setz mich kurz hin.",
        "So, einmal Röckchen richten und ausruhen.",
        "Auch eine Elfin braucht mal eine Pause.",
    )
    override val trip = listOf(
        "Huch! Wer hat das da hingestellt?",
        "Hoppla, ich bin über mein Kleid gestolpert!",
        "Aua, mein Elfenzeh!",
    )
    override val tripEmbarrassed = listOf(
        "Hat das jemand gesehen? Pssst!",
        "Das war eine ganz neue Tanzfigur.",
        "Alles gut, {name}, Elfen fallen nur sehr elegant.",
    )
    override val gift = listOf(
        "Ich habe eine Überraschung für dich, {name}!",
        "Pssst, es kommt ein Päckchen angeflogen.",
    )
    override val giftPlaced = listOf(
        "Das ist für dich, {name}. Mit einer Schleife!",
        "Überraschung! Aber erst später aufmachen.",
    )
    override val giftPop = listOf(
        "Ein Schwupps Elfenstaub, ein Geschenk!",
        "Sieh mal, {name}, was da plötzlich liegt!",
    )
    override val snowOn = listOf(
        "Oh, sieh nur nach oben, {name}!",
        "Es schneit! Die Wolken streuen Elfenstaub!",
    )
    override val snowLike = listOf(
        "Schneeflöckchen, Weißröckchen, komm zu mir!",
        "Alles glitzert, so schön!",
    )
    override val snowOff = listOf(
        "Jetzt reicht es mit dem Schnee, danke!",
        "Die Wolken sind leer, aber es war wunderschön.",
    )
    override val magic = listOf(
        "Hokuspokus Elfenstaub!",
        "Abrakadabra, Glitzer, komm herbei!",
    )
    override val surprised = listOf(
        "Huch, das war wohl mein Zauber!",
        "Huch, der Zauber hat geklappt!",
        "Ich wusste gar nicht, dass ich so zaubern kann!",
    )
    override val bow = listOf(
        "Vielen Dank, ihr Lieben!",
        "Klatsch, klatsch, danke schön, {name}!",
    )
    override val sneaky = listOf(
        "Pssst, ich nasche ganz leise.",
        "Das Plätzchen ist doch gar nicht für dich, {name}... oder?",
    )

    override val general = listOf(
        "Hallo {name}! Heute war ich wieder zauberhaft unterwegs!",
        "Pssst, {name}! Verrate nicht, dass ich hier war.",
        "{name}, du bist die beste Freundin oder der beste Freund, die eine Elfin haben kann!",
        "Elfen müssen nie ins Bett, aber ich bin trotzdem müde.",
        "Weißt du, {name}, ich sehe jede gute Tat, so klein sie auch ist!",
        "Im Elfenwald schmecken Beeren nach Sonnenschein.",
        "Heute Nacht habe ich im Zimmer Glitzer verteilt. Fast überall.",
        "Kicher, {name}, siehst du mich noch zwischen den Glitzerpunkten?",
        "Mein Name ist {wichtel} und ich liebe Lebkuchen!",
        "Elfen können sich ganz klein machen. Wusstest du das, {name}?",
        "Heute war ich ein bisschen übermütig. Nur ein ganz kleines bisschen.",
        "{name}, sind deine Zähne schon blitzblank? Ich habe vorhin geguckt!",
        "Eine Elfin hilft gern, aber manchmal verheddert sie sich in ihren Zöpfen.",
        "Ich habe einen Glitzerschatz im Zimmer versteckt. Rate mal, wo!",
        "Du bist mutig, {name}. Das gefällt mir.",
        "Brrr, der Boden ist kalt an meinen Elfenfüßen.",
        "Jeden Abend schaue ich, ob du schön träumst.",
        "Ich kann summen, drehen und flattern. Nur nicht gleichzeitig!",
        "{name}, ich wollte Sterne sammeln, aber dann bin ich eingenickt.",
        "Wenn du lachst, wird mein Elfenherz ganz warm.",
        "Oh, ein Zuckerkrümel! Den nehme ich mit.",
        "Ich habe ein Geheimnis, {name}. Es ist ein Zuckerstern.",
        "Hast du heute etwas Nettes gemacht? Dann bekommt die Elfin einen Stern.",
        "Alle Elfen der Welt winken dir zu, {name}!",
        "Manchmal verstecke ich mich im Vorhang und kichere leise.",
        "Meine Zipfelmütze ist die schönste weit und breit, findest du nicht?",
        "Heute Nacht hat es im Elfenwald geschneit.",
        "Ich bin klein, aber ich habe große Träume!",
        "{name}, du darfst mir gern einen Wunsch verraten. Aber ganz leise!",
        "Fegen macht mit einem Liedchen am meisten Spaß.",
        "Dieser Socken gehört bestimmt einem Riesen, nicht mir.",
        "Gute Nacht, {name}. Träum etwas Schönes!",
        "Der Besen und ich tanzen am liebsten zusammen.",
        "Ein bisschen Glitzer gehört zum Leben dazu.",
        "{name}, ich hab dich lieb. Ganz doll.",
        "Morgen habe ich wieder eine Überraschung. Aber verrate nichts!",
        "Ich flechte mir gleich neue Zöpfe!",
        "Sieh mal, mein Kleid dreht sich beim Tanzen!",
        "Elfenstaub macht alles ein bisschen schöner.",
        "Meine Schürze hat Taschen für Kekse.",
        "Ich habe die Zuckerstangen gezählt: Es sind genau drei!",
    )

    override val build = listOf(
        "Mit etwas Elfenstaub stelle ich {item} auf!",
        "Schwupps, hier kommt {item}!",
    )
    override val buildDone = listOf(
        "Fertig! Wie das glitzert!",
        "Schön geworden, nicht wahr?",
    )
    override val noBed = listOf(
        "Ich sehe hier gar kein Bett …",
    )
    override val bedNight = listOf(
        "Gute Nacht! Ab unter die Decke.",
        "Zeit zum Träumen, gute Nacht!",
    )
    override val bedMorning = listOf(
        "Mmh? Ist schon Morgen?",
        "Gähn, habe ich schön geträumt?",
    )

    override val chat = listOf(
        "Weißt du was, {kind}? Du bist wunderbar!",
        "{wichtel}, die Elfin, mag dich sehr.",
        "Wann gibt es Lebkuchen?",
        "Hast du heute schon gekichert, {kind}?",
        "Ich wette, ich tanze schöner als du.",
        "Im Elfenwald ist es heute sehr ruhig.",
    )
}
