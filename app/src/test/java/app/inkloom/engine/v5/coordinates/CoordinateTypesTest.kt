package app.inkloom.engine.v5.coordinates

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTypesTest {
    @Test fun `contact transform round trips across scale rotation and translation`() {
        val radians = Math.toRadians(37.0)
        val scale = 7.5f
        val transform = ContactTransform.create(
            42L,
            Affine(
                (cos(radians) / scale).toFloat(),
                (-sin(radians) / scale).toFloat(),
                (sin(radians) / scale).toFloat(),
                (cos(radians) / scale).toFloat(),
                -183.25f,
                91.75f,
            ),
        )
        val view = ViewPoint(1999.25f, 1201.75f)
        val roundTrip = transform.toView(transform.toDocument(view))
        assertEquals(view.x, roundTrip.x, .001f)
        assertEquals(view.y, roundTrip.y, .001f)
    }
}

