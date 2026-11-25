package app.inkloom.engine.v5.material

import app.inkloom.brushpresetv5.ProceduralTipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationTipsTest {
    private fun row(mask: BrushTipMask, y: Int) = (0 until mask.width).map { x ->
        (mask.coverageAt((x + .5f) / mask.width, (y + .5f) / mask.height) * 255f).toInt()
    }

    @Test
    fun ribbonTipsAreTallAndTileAlongTheirHeight() {
        listOf(ProceduralTipKind.LACE, ProceduralTipKind.SCALLOP, ProceduralTipKind.HAIR, ProceduralTipKind.BARK).forEach { kind ->
            val mask = BrushTipMask.procedural(kind, 96, 7, .5f, 1f)
            assertEquals("$kind is half as wide as tall", mask.height / 2, mask.width)
            // The pattern repeats over the tip, so its first and last rows match and the ribbon joins seamlessly.
            val first = row(mask, 0); val last = row(mask, mask.height - 1)
            val difference = first.indices.sumOf { kotlin.math.abs(first[it] - last[it]) } / first.size
            assertTrue("$kind joins seamlessly: $difference", difference < 40)
            assertTrue("$kind has content", first.any { it > 128 } || row(mask, mask.height / 2).any { it > 128 })
        }
    }

    @Test
    fun laceHasEyeletsAndScallops() {
        val lace = DecorationTips.lace(96)
        val middle = row(lace, 24)
        assertTrue("an eyelet is open inside the band", middle[13] < 60)
        assertTrue("the band is solid around it", middle[3] > 200)
        assertTrue("the scallop reaches out at its crest", middle[44] > 128 && row(lace, 0)[44] < 60)
    }

    @Test
    fun stampTipsHaveTheirShapes() {
        val chain = DecorationTips.chain(96)
        assertTrue("a link is open in the middle", chain.coverageAt(.5f, .5f) < .1f && chain.coverageAt(.92f, .5f) > .5f)
        val stitch = DecorationTips.stitch(96)
        assertTrue("a dash along x", stitch.coverageAt(.5f, .5f) > .9f && stitch.coverageAt(.5f, .2f) < .1f)
        val pearl = DecorationTips.pearl(96)
        assertTrue("a bead with a light catch", pearl.coverageAt(.34f, .34f) < pearl.coverageAt(.65f, .6f))
        val leaf = DecorationTips.leaf(96)
        assertTrue("a leaf stands up and narrows", leaf.coverageAt(.5f, .5f) > .3f && leaf.coverageAt(.8f, .5f) < .1f && leaf.coverageAt(.2f, .5f) < .1f)
        val grass = DecorationTips.grass(96, 3)
        assertTrue("a blade rises from the bottom", grass.coverageAt(.5f, .95f) > .5f && grass.coverageAt(.1f, .95f) < .1f)
    }
}
