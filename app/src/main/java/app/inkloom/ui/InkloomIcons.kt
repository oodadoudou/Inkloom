package app.inkloom.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Original 24-unit tool silhouettes, matching the editor's outlined controls. */
internal object InkloomIcons {
    val Eraser: ImageVector by lazy {
        ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 12.4f)
                lineTo(12.8f, 3.6f)
                curveTo(13.6f, 2.8f, 14.7f, 2.8f, 15.5f, 3.6f)
                lineTo(20.4f, 8.5f)
                curveTo(21.2f, 9.3f, 21.2f, 10.4f, 20.4f, 11.2f)
                lineTo(11.6f, 20f)
                lineTo(7.8f, 20f)
                lineTo(4f, 16.2f)
                curveTo(3f, 15.2f, 3f, 13.4f, 4f, 12.4f)
                close()
                moveTo(8f, 8.4f)
                lineTo(15.6f, 16f)
                moveTo(11.6f, 20f)
                lineTo(21f, 20f)
            }
        }.build()
    }
}
