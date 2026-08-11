package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.InkStroke

/** Original built-in tips for system/spray presets that do not reference an imported material. */
internal object ProceduralBrushTip {
    fun supports(stroke: InkStroke): Boolean =
        stroke.tipAssetPath == null && stroke.tipAssetPaths.isEmpty() && (
            stroke.spray || stroke.stampCount > 1 || stroke.engineKind == BrushEngineKind.AIRBRUSH ||
                stroke.brushId.startsWith("system-decoration-") || stroke.brushId in GRAPHITE_BRUSH_IDS
            )

    fun create(stroke: InkStroke): Bitmap? {
        if (!supports(stroke)) return null
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        when {
            stroke.brushId in GRAPHITE_BRUSH_IDS -> drawGraphiteTip(bitmap, stroke)
            stroke.brushId.contains("sparkle", true) -> {
                val center = SIZE / 2f
                val outer = SIZE * .45f
                val inner = SIZE * .11f
                val star = Path()
                repeat(16) { index ->
                    val angle = -Math.PI / 2.0 + index * Math.PI / 8.0
                    val radius = if (index % 2 == 0) outer else inner
                    val x = center + kotlin.math.cos(angle).toFloat() * radius
                    val y = center + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                canvas.drawPath(star, paint)
            }
            stroke.brushId.contains("hatching", true) -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = SIZE * .1f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(SIZE * .16f, SIZE * .84f, SIZE * .84f, SIZE * .16f, paint)
                canvas.drawLine(SIZE * .16f, SIZE * .16f, SIZE * .84f, SIZE * .84f, paint)
            }
            else -> canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE * .42f, paint)
        }
        return bitmap
    }

    /** A small deterministic alpha material gives every graphite grade its own tooth without
     * shipping opaque proprietary brush artwork. Live, tile, thumbnail and export paths all use
     * this exact bitmap. */
    private fun drawGraphiteTip(bitmap: Bitmap, stroke: InkStroke) {
        val grade = when {
            stroke.brushId.contains("6b") -> .54f
            stroke.brushId.contains("2b") -> .66f
            stroke.brushId.contains("hb") -> .76f
            stroke.brushId.contains("h-pencil") -> .84f
            else -> .93f
        }
        val pixels = IntArray(SIZE * SIZE)
        val center = (SIZE - 1) / 2f
        val radius = SIZE * .46f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val dx = x - center
            val dy = y - center
            val normalized = kotlin.math.sqrt(dx * dx + dy * dy) / radius
            if (normalized >= 1f) continue
            val edge = ((1f - normalized) / (1f - stroke.hardness.coerceIn(.05f, .98f))).coerceIn(0f, 1f)
            val hash = ((x * 73856093) xor (y * 19349663) xor stroke.brushId.hashCode())
            val noise = (hash ushr 8 and 0xFF) / 255f
            val tooth = (grade + noise * (1f - grade)).coerceIn(0f, 1f)
            val dropout = ((hash ushr 17) and 0xFF) / 255f
            val retained = if (dropout < (1f - grade) * .34f) .18f else 1f
            val alpha = (255f * edge * tooth * retained).toInt().coerceIn(0, 255)
            pixels[y * SIZE + x] = Color.argb(alpha, 255, 255, 255)
        }
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
    }

    private const val SIZE = 64
    private val GRAPHITE_BRUSH_IDS = setOf(
        "system-technical-pencil",
        "system-h-pencil",
        "system-hb-pencil",
        "system-2b-pencil",
        "system-6b-pencil",
    )
}
