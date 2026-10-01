package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/** Local UV lattice over original content. Text and source image assets are never baked. */
@Serializable
data class ElementDeformation(
    val columns: Int,
    val rows: Int,
    val vertices: List<ShapeVertex>,
) {
    init {
        require(columns in 2..65 && rows in 2..65)
        require(vertices.size == columns * rows && vertices.all { it.x.isFinite() && it.y.isFinite() })
    }

    fun sample(u: Float, v: Float): ShapeVertex {
        val gx = u.coerceIn(0f, 1f) * (columns - 1)
        val gy = v.coerceIn(0f, 1f) * (rows - 1)
        val x = gx.toInt().coerceAtMost(columns - 2)
        val y = gy.toInt().coerceAtMost(rows - 2)
        val tx = gx - x; val ty = gy - y
        val a = vertices[y * columns + x]; val b = vertices[y * columns + x + 1]
        val c = vertices[(y + 1) * columns + x]; val d = vertices[(y + 1) * columns + x + 1]
        fun mix(a: Float, b: Float, c: Float, d: Float) =
            a * (1 - tx) * (1 - ty) + b * tx * (1 - ty) + c * (1 - tx) * ty + d * tx * ty
        return ShapeVertex(mix(a.x, b.x, c.x, d.x), mix(a.y, b.y, c.y, d.y))
    }
}

/** Page-space position, including retained distortion and rotation. */
fun PageElement.contentPoint(u: Float, v: Float, pageWidth: Float, pageHeight: Float): ShapeVertex {
    val local = deformation?.sample(u, v) ?: ShapeVertex(u, v)
    val dx = (local.x - .5f) * width; val dy = (local.y - .5f) * height
    val angle = Math.toRadians(rotationDegrees.toDouble())
    val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
    return ShapeVertex(x * pageWidth + width / 2 + dx * c - dy * s,
        y * pageHeight + height / 2 + dx * s + dy * c)
}

fun PageElement.withDeformation(value: ElementDeformation?): PageElement = when (this) {
    is PageElement.Text -> copy(deformation = value)
    is PageElement.Sticky -> copy(deformation = value)
    is PageElement.Shape -> copy(deformation = value)
    is PageElement.Image -> copy(deformation = value)
    is PageElement.Fill -> copy(deformation = value)
    is PageElement.Link -> copy(deformation = value)
}

/** Bounds used by the object interaction plane, without changing editable source geometry. */
fun PageElement.displayBounds(pageWidth: Float, pageHeight: Float): List<ShapeVertex> =
    if (deformation == null) listOf(contentPoint(0f, 0f, pageWidth, pageHeight), contentPoint(1f, 0f, pageWidth, pageHeight),
        contentPoint(1f, 1f, pageWidth, pageHeight), contentPoint(0f, 1f, pageWidth, pageHeight))
    else deformation!!.vertices.map { uv ->
        val base = withDeformation(null)
        base.contentPoint(uv.x, uv.y, pageWidth, pageHeight)
    }
