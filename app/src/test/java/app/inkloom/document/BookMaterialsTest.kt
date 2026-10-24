package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMaterialsTest {
    @Test fun hardcoverBoardsAreThickerWiderAndRigid() {
        val soft = BookGeometry(1.4f, 6, BookStitch.SIDE)
        val hard = BookGeometry(1.4f, 6, BookStitch.SIDE, hardCover = true)
        assertTrue(hard.isBoard(0) && hard.isBoard(5) && !hard.isBoard(2))
        assertEquals(soft.thickness - 2 * soft.leafThickness + 2 * hard.boardThickness, hard.thickness, 1e-5f)
        // Heights stack the real thicknesses.
        assertEquals(hard.thickness - hard.boardThickness, hard.unturnedHeight(0), 1e-5f)
        assertEquals(hard.boardThickness, hard.turnedHeight(1), 1e-5f)
        assertTrue(hard.widthOf(0) > 1f && hard.heightScaleOf(5) > 1f && hard.widthOf(2) == 1f)
        // Mid-turn a board is a straight line; a soft leaf curls.
        fun straightness(profile: FloatArray): Float {
            val n = profile.size / 2 - 1
            val ax = profile[0]; val az = profile[1]; val bx = profile[2 * n]; val bz = profile[2 * n + 1]
            return (0..n).maxOf { i -> kotlin.math.abs((bx - ax) * (profile[2 * i + 1] - az) - (bz - az) * (profile[2 * i] - ax)) }
        }
        assertTrue(straightness(hard.profile(0, .5f, 1f, 1f)) < 1e-4f)
        assertTrue(straightness(soft.profile(0, .5f, 1f, 1f)) > 1e-3f)
    }

    @Test fun shineGrowsWithTheFinishAndAtGlancingAngles() {
        val facing = floatArrayOf(0f, 0f, 1f)
        val head = BookCamera.normalize(floatArrayOf(0f, 0f, 1f))
        val glancing = BookCamera.normalize(floatArrayOf(.9f, 0f, .2f))
        assertEquals(0f, BookLight.highlight(facing, head, BookFinish.NONE.shine, BookFinish.NONE.sharpness), 0f)
        val coated = BookLight.highlight(facing, glancing, BookFinish.COATED.shine, BookFinish.COATED.sharpness)
        val gloss = BookLight.highlight(facing, glancing, BookFinish.GLOSS.shine, BookFinish.GLOSS.sharpness)
        assertTrue(gloss > coated && coated > 0f)
        // With the light's reflection made very sharp, what remains is the sheen at glancing angles.
        assertTrue(BookLight.highlight(facing, glancing, .5f, 400f) > BookLight.highlight(facing, head, .5f, 400f) + .05f)
    }

    @Test fun aLeafCloseAboveDarkensTheStackMoreThanOneFarAway() {
        val stack = floatArrayOf(0f, 0f, .5f, 0f, 1f, 0f)
        val near = floatArrayOf(0f, .02f, .5f, .05f, 1f, .1f)
        val far = floatArrayOf(0f, .3f, .5f, .6f, 1f, .9f)
        val nearShade = BookLight.occlusion(stack, near); val farShade = BookLight.occlusion(stack, far)
        assertTrue(nearShade.indices.all { nearShade[it] >= farShade[it] })
        assertTrue(nearShade[0] > .3f && farShade[2] == 0f)
        // Nothing above, nothing darkened.
        assertTrue(BookLight.occlusion(stack, floatArrayOf(0f, -.1f, 1f, -.1f)).all { it == 0f })
    }
}
