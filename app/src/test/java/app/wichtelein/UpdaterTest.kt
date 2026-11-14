package app.wichtelein

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun buildNumberReadsDigits() {
        assertEquals(17L, Updater.buildNumber("build-17"))
        assertEquals(-1L, Updater.buildNumber("latest"))
        assertEquals(-1L, Updater.buildNumber(null))
    }
}
