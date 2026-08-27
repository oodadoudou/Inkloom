package app.inkloom.engine.v5.coordinates

import kotlin.math.abs

/** Coordinates in immutable document pixels. Never pass View or normalized coordinates here. */
data class DocumentPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "DocumentPoint must be finite" }
    }
}

/** Coordinates in the receiving Android View. */
data class ViewPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "ViewPoint must be finite" }
    }
}

data class DocumentRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite)) { "DocumentRect must be finite" }
        require(left <= right && top <= bottom) { "DocumentRect must be ordered" }
    }

    val isEmpty: Boolean get() = left == right || top == bottom

    fun union(other: DocumentRect): DocumentRect = DocumentRect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    fun outset(radius: Float): DocumentRect {
        require(radius.isFinite() && radius >= 0f)
        return DocumentRect(left - radius, top - radius, right + radius, bottom + radius)
    }
}

/**
 * Frozen affine pair for one contact. V5 maps an input packet exactly once at ingress and carries
 * document coordinates afterward. The inverse is retained for cursor/presentation round trips.
 */
class ContactTransform private constructor(
    val generation: Long,
    private val viewToDocument: Affine,
    private val documentToView: Affine,
) {
    fun toDocument(point: ViewPoint): DocumentPoint = viewToDocument.map(point).let {
        DocumentPoint(it.x, it.y)
    }

    fun toView(point: DocumentPoint): ViewPoint = documentToView.map(ViewPoint(point.x, point.y))

    companion object {
        fun create(generation: Long, viewToDocument: Affine): ContactTransform =
            ContactTransform(generation, viewToDocument, viewToDocument.inverse())
    }
}

/** Row-major 2D affine matrix: [a c tx; b d ty; 0 0 1]. */
data class Affine(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float,
) {
    init {
        require(listOf(a, b, c, d, tx, ty).all(Float::isFinite)) { "Affine must be finite" }
    }

    fun map(point: ViewPoint): ViewPoint = ViewPoint(
        a * point.x + c * point.y + tx,
        b * point.x + d * point.y + ty,
    )

    fun inverse(): Affine {
        val determinant = a * d - b * c
        require(abs(determinant) > 1e-8f) { "Affine is not invertible" }
        val inverseDeterminant = 1f / determinant
        val ia = d * inverseDeterminant
        val ib = -b * inverseDeterminant
        val ic = -c * inverseDeterminant
        val id = a * inverseDeterminant
        return Affine(
            ia,
            ib,
            ic,
            id,
            -(ia * tx + ic * ty),
            -(ib * tx + id * ty),
        )
    }

    companion object {
        val Identity = Affine(1f, 0f, 0f, 1f, 0f, 0f)
    }
}
