package app.inkloom.engine.v3.render

import app.inkloom.document.InkStroke

internal interface ActiveStrokeCompositor {
    fun render(strokes: List<InkStroke>)
    fun commit(strokes: List<InkStroke>)
    fun cancel()
}
