package app.inkloom.settings

import org.junit.Assert.*
import org.junit.Test

class StarterColorSetsTest {
    @Test fun addingPreservesUserPalettesAndCreatesIndependentEditableCopies() {
        val original = ColorSetLibrary().createSet("My colors", "user")
            .insertSwatch("user", 0x80765533.toInt(), "custom", id = "custom")
        val first = StarterColorSet.LINE_ART.addTo(original, UiLanguage.ENGLISH)
        val twice = StarterColorSet.LINE_ART.addTo(first, UiLanguage.ENGLISH)
        assertEquals(original.sets.first(), twice.sets.first())
        val copyA = twice.sets[1]; val copyB = twice.sets[2]
        assertNotEquals(copyA.id, copyB.id)
        assertTrue(copyA.swatches.map { it.id }.intersect(copyB.swatches.map { it.id }.toSet()).isEmpty())
        val modified = twice.replaceSwatch(copyB.id, copyB.swatches.first().id, 0xff112233.toInt())
        assertEquals(first.activeSet, modified.sets[1])
        val without = modified.deleteSet(copyB.id)
        val restored = StarterColorSet.LINE_ART.addTo(without, UiLanguage.ENGLISH)
        assertEquals(0xff000000.toInt(), restored.activeSet!!.swatches.first().color)
    }

    @Test fun sourceOrderLocalizationAndOpaqueColorsSurviveRoundTrip() {
        for (language in listOf(UiLanguage.ENGLISH, UiLanguage.SIMPLIFIED_CHINESE)) {
            for (preset in StarterColorSet.entries) {
                val added = preset.addTo(ColorSetLibrary(), language)
                val decoded = ColorSetLibraryCodec.decode(ColorSetLibraryCodec.encode(added))
                assertEquals(added, decoded)
                assertEquals(preset.displayName(language), decoded.activeSet!!.name)
                assertEquals(preset.swatches(language), decoded.activeSet!!.swatches.map { it.color to it.name })
                assertTrue(decoded.activeSet!!.swatches.all { it.color ushr 24 == 255 && it.name.isNotBlank() })
            }
        }
    }

    @Test fun grayscaleRunsFromBlackToWhiteWithoutRepeatedOrInvertedSteps() {
        val ramp = StarterColorSet.GRAYSCALE.swatches(UiLanguage.ENGLISH).map { it.first and 255 }
        assertEquals(0, ramp.first()); assertEquals(255, ramp.last())
        assertTrue(ramp.zipWithNext().all { (before, after) -> before < after })
    }
}
