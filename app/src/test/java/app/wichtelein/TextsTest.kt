package app.wichtelein

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextsTest {
    @Test fun fillReplacesBothPlaceholders() {
        assertEquals("Hallo Max, ich bin Pippo!", fill("Hallo {kind}, ich bin {wichtel}!", "Max", "Pippo"))
    }

    @Test fun fillReplacesRepeatedPlaceholders() {
        assertEquals("Max und Max", fill("{kind} und {kind}", "Max", "x"))
    }

    @Test fun parseNamesSplitsAndTrims() {
        assertEquals(listOf("Max", "Lena", "Opa Karl"), parseNames(" Max, Lena ;Opa Karl,, \n"))
        assertTrue(parseNames("  ").isEmpty())
    }

    @Test fun parseLinesIgnoresEmptyLines() {
        assertEquals(listOf("Eins", "Zwei"), parseLines("Eins\n\n  Zwei  \n"))
    }

    @Test fun bubbleDurationGrowsWithText() {
        assertTrue(bubbleDurationMs("Hallo, ich bin ein langer Satz") > bubbleDurationMs("Hi"))
        assertTrue(bubbleDurationMs("") >= 1800L)
    }

    @Test fun pickWeightedNeverReturnsExcluded() {
        val rnd = Random(42)
        repeat(2000) { assertFalse(pickWeighted(listOf(3, 3, 3, 3), exclude = 2, rnd = rnd) == 2) }
    }

    @Test fun pickWeightedRespectsZeroWeights() {
        val rnd = Random(7)
        repeat(2000) { assertEquals(1, pickWeighted(listOf(0, 5, 0), exclude = -1, rnd = rnd)) }
    }

    @Test fun pickWeightedFallsBackWhenOnlyExcludedPossible() {
        assertEquals(1, pickWeighted(listOf(0, 4, 0), exclude = 1))
    }

    @Test fun pickWeightedCoversAllOptions() {
        val rnd = Random(1)
        val seen = HashSet<Int>()
        repeat(500) { seen += pickWeighted(listOf(1, 1, 1, 1), exclude = -1, rnd = rnd) }
        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test fun allLinePoolsAreNonEmpty() {
        listOf(Lines.greet, Lines.tidy, Lines.tidyDone, Lines.chaos, Lines.dance, Lines.tumble, Lines.dizzy,
            Lines.wave, Lines.sleep, Lines.wake, Lines.snack, Lines.hide, Lines.hideFound, Lines.chat)
            .forEach { assertTrue(it.isNotEmpty()) }
    }
}
