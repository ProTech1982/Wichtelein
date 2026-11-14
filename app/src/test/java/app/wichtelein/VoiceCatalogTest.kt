package app.wichtelein

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    @Test fun idsAreUniqueAndExactlyOneIsBundled() {
        assertEquals(VoiceCatalog.all.size, VoiceCatalog.all.map { it.id }.toSet().size)
        assertEquals(1, VoiceCatalog.all.count { it.bundled })
        assertTrue(VoiceCatalog.find(VoiceCatalog.DEFAULT_ID).bundled)
    }

    @Test fun downloadableVoicesHaveHttpsUrlAndChecksum() {
        VoiceCatalog.all.filter { !it.bundled }.forEach {
            assertTrue(it.url, it.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/"))
            assertTrue(it.url.endsWith(".tar.bz2"))
            assertTrue("${it.id} sha", Regex("[0-9a-f]{64}").matches(it.sha256 ?: ""))
        }
    }

    @Test fun unknownIdFallsBackToDefault() {
        assertEquals(VoiceCatalog.DEFAULT_ID, VoiceCatalog.find("gibt-es-nicht").id)
    }

    @Test fun fillSupportsNameAndLegacyKindPlaceholder() {
        assertEquals("Hallo Max, ich bin Pippo", fill("Hallo {name}, ich bin {wichtel}", "Max", "Pippo"))
        assertEquals("Max", fill("{kind}", "Max", "x"))
    }
}
