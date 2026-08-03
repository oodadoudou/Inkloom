package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CspAntiAliasTest {
    @Test fun modesAreNormalizedAndNoneUsesHardCoverage() {
        assertEquals(CspAntiAlias.NONE, CspAntiAlias.normalize(-8))
        assertEquals(CspAntiAlias.STRONG, CspAntiAlias.normalize(12))
        assertEquals(0f, CspAntiAlias.shapeCoverage(.49f, CspAntiAlias.NONE), 0f)
        assertEquals(1f, CspAntiAlias.shapeCoverage(.5f, CspAntiAlias.NONE), 0f)
    }

    @Test fun weakMediumAndStrongRetainProgressivelyMoreEdgeCoverage() {
        val weak = CspAntiAlias.shapeCoverage(.25f, CspAntiAlias.WEAK)
        val medium = CspAntiAlias.shapeCoverage(.25f, CspAntiAlias.MEDIUM)
        val strong = CspAntiAlias.shapeCoverage(.25f, CspAntiAlias.STRONG)

        assertTrue(weak < medium)
        assertTrue(medium < strong)
        assertEquals(.0625f, weak, .0001f)
        assertEquals(.5f, strong, .0001f)
    }

    @Test fun invalidCoverageCannotPoisonRasterMath() {
        assertEquals(0f, CspAntiAlias.shapeCoverage(Float.NaN, CspAntiAlias.STRONG), 0f)
        assertEquals(0f, CspAntiAlias.shapeCoverage(Float.NEGATIVE_INFINITY, CspAntiAlias.MEDIUM), 0f)
        assertEquals(1f, CspAntiAlias.shapeCoverage(2f, CspAntiAlias.WEAK), 0f)
    }
}
