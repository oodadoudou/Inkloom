package app.inkloom.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardColorHexTest {
    @Test fun rgbAndArgbRoundTripWithoutDroppingAlpha() {
        assertEquals("#1234AB", StandardColorHex.format(0xff1234ab.toInt()))
        assertEquals(0xff1234ab.toInt(), StandardColorHex.parse("#1234ab"))
        assertEquals("#801234AB", StandardColorHex.format(0x801234ab.toInt()))
        assertEquals(0x801234ab.toInt(), StandardColorHex.parse("801234AB"))
        assertNull(StandardColorHex.parse("#12"))
        assertNull(StandardColorHex.parse("#ZZZZZZ"))
    }
}
