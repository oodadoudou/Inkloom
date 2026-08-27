package app.inkloom.engine.v5.material

import org.junit.Assert.assertEquals
import org.junit.Test

class PressureResponseTest {
    @Test
    fun curveAndMinimumAreIndependentAndDeterministic() {
        val response = PressureResponse(true, .2f, listOf(0f to 0f, .5f to .25f, 1f to 1f))
        assertEquals(.2f, response.evaluate(0f), .0001f)
        assertEquals(.4f, response.evaluate(.5f), .0001f)
        assertEquals(.7f, response.evaluate(.75f), .0001f)
        assertEquals(1f, response.evaluate(1f), .0001f)
    }

    @Test
    fun disabledResponseDoesNotReadPressureOrCurve() {
        val response = PressureResponse(false, 0f, listOf(.5f to 0f))
        assertEquals(1f, response.evaluate(0f), .0001f)
        assertEquals(1f, response.evaluate(1f), .0001f)
    }
}

