package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CspColorMixTest {
    @Test fun sutInferenceUsesThePersistedParameterFamilyInsteadOfEnableBitAsAnEnum() {
        assertEquals(null, CspColorMixMode.inferFromSut(false, .5f, .5f, .5f, 20f, true))
        assertEquals(CspColorMixMode.RUNNING_COLOR, CspColorMixMode.inferFromSut(true, 0f, 0f, .1f, 10f, false))
        assertEquals(CspColorMixMode.RUNNING_COLOR, CspColorMixMode.inferFromSut(true, 0f, 0f, .1f, 0f, true))
        assertEquals(CspColorMixMode.SMEAR, CspColorMixMode.inferFromSut(true, 0f, 0f, .2f, 0f, false))
        assertEquals(CspColorMixMode.BLEND, CspColorMixMode.inferFromSut(true, .7f, .4f, .2f, 0f, false))
    }
}
