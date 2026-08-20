package app.inkloom.engine.v3.brush

import app.inkloom.engine.v3.geometry.DocumentPoint

enum class MaterialCoordinateSpace { DOCUMENT, BRUSH_LOCAL, STROKE }

data class BrushLocalPoint(val x: Float, val y: Float)
data class StrokeMaterialPoint(val distance: Float, val across: Float)

/** Explicitly labels the coordinate basis used by a material sample. */
internal sealed interface MaterialCoordinateContext {
    val space: MaterialCoordinateSpace

    data class Document(val point: DocumentPoint) : MaterialCoordinateContext {
        override val space = MaterialCoordinateSpace.DOCUMENT
    }

    data class BrushLocal(val point: BrushLocalPoint) : MaterialCoordinateContext {
        override val space = MaterialCoordinateSpace.BRUSH_LOCAL
    }

    data class Stroke(val point: StrokeMaterialPoint) : MaterialCoordinateContext {
        override val space = MaterialCoordinateSpace.STROKE
    }

    companion object {
        fun document(x: Float, y: Float) = Document(DocumentPoint(x, y))
        fun brushLocal(x: Float, y: Float) = BrushLocal(BrushLocalPoint(x, y))
        fun stroke(distance: Float, across: Float) = Stroke(StrokeMaterialPoint(distance, across))
    }
}
