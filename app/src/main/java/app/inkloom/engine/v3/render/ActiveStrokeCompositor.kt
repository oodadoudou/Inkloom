package app.inkloom.engine.v3.render

import app.inkloom.document.InkStroke

internal interface ActiveStrokeCompositor {
    fun render(strokes: List<InkStroke>)
    fun commit(strokes: List<InkStroke>)
    fun cancel()

    /** True while the low-latency front buffer still holds uncommitted active ink. */
    val hasActiveInk: Boolean get() = false
}
