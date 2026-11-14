package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun profilesRoundTripThroughJson() {
        val list = listOf(Profile("a", "Max"), Profile("b", "Lena, Paul"))
        assertEquals(list, SettingsStore.parseProfiles(SettingsStore.profilesToJson(list)))
        assertTrue(SettingsStore.parseProfiles(null).isEmpty())
        assertTrue(SettingsStore.parseProfiles("kaputt{").isEmpty())
    }

    @Test fun withProfileAddsAndActivates() {
        val s = Settings().withProfile("Max").withProfile("Lena")
        assertEquals(2, s.profiles.size)
        assertEquals("Lena", s.activeProfile()?.name)
        assertEquals(listOf("Lena"), s.kidList())
    }

    @Test fun siblingsInOneProfileGiveSeveralNames() {
        val s = Settings().withProfile("Max, Lena")
        assertEquals(listOf("Max", "Lena"), s.kidList())
    }

    @Test fun noProfileMeansNoName() {
        assertNull(Settings().activeProfile())
        assertTrue(Settings().kidList().isEmpty())
    }

    @Test fun enabledScenesExcludeDisabledOnes() {
        assertNull(Settings().enabledScenes())
        val s = Settings(disabledScenes = setOf("brav_fegen"))
        val e = s.enabledScenes()!!
        assertTrue("brav_fegen" !in e)
        assertEquals(Scenes.all.size - 1, e.size)
    }

    @Test fun activeProfileFallsBackToFirst() {
        val s = Settings(profiles = listOf(Profile("a", "Max")), activeProfileId = "gibt-es-nicht")
        assertEquals("Max", s.activeProfile()?.name)
    }
}
