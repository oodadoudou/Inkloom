package app.inkloom

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** Physical targets from the visible CSP-style wheel contract, independent of production hit testing. */
internal object ColorWheelTestCoordinates {
    fun hue(ring: Rect, hue: Float): Offset {
        val angle = Math.toRadians((hue + 210f).toDouble())
        return Offset(ring.exactCenterX() + cos(angle).toFloat() * ring.width() * .43f,
            ring.exactCenterY() + sin(angle).toFloat() * ring.height() * .43f)
    }
    fun triangle(field: Rect, saturation: Float, value: Float): Offset =
        Offset(field.left.toFloat(), field.top.toFloat()) + triangle(field.width().toFloat(), field.height().toFloat(), saturation, value)
    fun triangle(width: Float, height: Float, saturation: Float, value: Float): Offset =
        Offset(width * saturation * value, height * (1f - value + saturation * value * .5f))
}
