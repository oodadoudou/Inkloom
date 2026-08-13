package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CspColorMixTest {
    @Test fun sutInferenceUsesThePersistedParameterFamilyInsteadOfEnableBitAsAnEnum() {
        assertEquals(null, CspColorMixMode.inferFromSut(false, .5f, .5f, .5f, 20f, true))
        assertEquals(CspColorMixMode.RUNNING_COLOR, CspColorMixMode.inferFromSut(true, 0f, 0f, .1f, 10f, false))
        assertEquals(CspColorMixMode.RUNNING_COLOR, CspColorMixMode.inferFromSut(true, 0f, 0f, .1f, 0f, true))
        assertEquals(CspColorMixMode.SMEAR, CspColorMixMode.inferFromSut(true, 0f, 0f, .2f, 0f, false))
        assertEquals(CspColorMixMode.BLEND, CspColorMixMode.inferFromSut(true, .7f, .4f, .2f, 0f, false))
    }

    @Test fun standardMixIsEncodedRgbAndPerceptualMixUsesASeparateColorPath() {
        val standard = CspPigmentMixer.mixArgb(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), .5f, CspPigmentMixingMode.STANDARD, 3)
        assertEquals(0xFF800080.toInt(), standard)
        val perceptual = CspPigmentMixer.mixArgb(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), .5f, CspPigmentMixingMode.PERCEPTUAL, 3)
        assertNotEquals(standard, perceptual)
        assertEquals(0xFF, perceptual ushr 24)
    }

    @Test fun perceptualBrightnessCorrectionIsMonotonicAndDeterministic() {
        val low = CspPigmentMixer.mixArgb(0xFF00A040.toInt(), 0xFF8040FF.toInt(), .5f, CspPigmentMixingMode.PERCEPTUAL, 1)
        val lowAgain = CspPigmentMixer.mixArgb(0xFF00A040.toInt(), 0xFF8040FF.toInt(), .5f, CspPigmentMixingMode.PERCEPTUAL, 1)
        val high = CspPigmentMixer.mixArgb(0xFF00A040.toInt(), 0xFF8040FF.toInt(), .5f, CspPigmentMixingMode.PERCEPTUAL, 5)
        assertEquals(low, lowAgain)
        fun brightness(color: Int) = (color ushr 16 and 0xFF) + (color ushr 8 and 0xFF) + (color and 0xFF)
        assertTrue(brightness(high) > brightness(low))
    }

    @Test fun exactEndpointsPreserveAuthoredPigment() {
        val existing = 0x3F123456
        val fresh = 0xCFAABBCC.toInt()
        assertEquals(existing, CspPigmentMixer.mixArgb(existing, fresh, 0f, CspPigmentMixingMode.PERCEPTUAL, 5))
        assertEquals(fresh, CspPigmentMixer.mixArgb(existing, fresh, 1f, CspPigmentMixingMode.PERCEPTUAL, 1))
    }
}
