package app.inkloom.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusInputClassifierTest {
    @Test fun fingerDrawingRemainsOptIn() {
        assertTrue(StylusInputClassifier.shouldNavigateOrRejectFinger(false, false, 1, 8f, Long.MAX_VALUE))
        assertFalse(StylusInputClassifier.shouldNavigateOrRejectFinger(false, true, 1, 8f, Long.MAX_VALUE))
    }

    @Test fun recentPenAndLargeContactRejectPalmEvenWhenFingerDrawingIsOn() {
        assertTrue(StylusInputClassifier.shouldNavigateOrRejectFinger(false, true, 1, 8f, 150L))
        assertTrue(StylusInputClassifier.shouldNavigateOrRejectFinger(false, true, 1, 60f, Long.MAX_VALUE))
        assertTrue(StylusInputClassifier.shouldNavigateOrRejectFinger(false, true, 2, 8f, Long.MAX_VALUE))
    }

    @Test fun stylusIsNeverReclassifiedAsFingerNavigation() {
        assertFalse(StylusInputClassifier.shouldNavigateOrRejectFinger(true, false, 2, 80f, 0L))
    }
}
