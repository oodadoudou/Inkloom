package app.inkloom.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorRelationsTest {
    @Test fun harmonyModesUseExpectedHueRelationships() {
        assertEquals(listOf(0xffff0000.toInt(), 0xff00ffff.toInt()), ColorRelations.harmony(0f, 1f, 1f, ColorHarmony.COMPLEMENTARY))
        assertEquals(3, ColorRelations.harmony(30f, .7f, .8f, ColorHarmony.TRIADIC).distinct().size)
        assertEquals(4, ColorRelations.harmony(30f, .7f, .8f, ColorHarmony.TETRADIC).distinct().size)
    }

    @Test fun cornerGridPreservesCornersAndUsesLinearLightCenter() {
        val white = 0xffffffff.toInt(); val black = 0xff000000.toInt(); val red = 0xffff0000.toInt(); val blue = 0xff0000ff.toInt()
        assertEquals(red, ColorRelations.cornerMix(red, blue, black, white, 0, 0, 5))
        assertEquals(blue, ColorRelations.cornerMix(red, blue, black, white, 4, 0, 5))
        assertEquals(black, ColorRelations.cornerMix(red, blue, black, white, 0, 4, 5))
        assertEquals(white, ColorRelations.cornerMix(red, blue, black, white, 4, 4, 5))
        val center = ColorRelations.cornerMix(black, white, black, white, 2, 2, 5)
        assertTrue("Linear-light midpoint should be lighter than direct #808080", (center and 0xff) > 170)
    }
}
