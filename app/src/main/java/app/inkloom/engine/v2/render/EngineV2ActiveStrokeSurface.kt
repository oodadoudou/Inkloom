package app.inkloom.engine.v2.render

import app.inkloom.document.InkStroke

internal interface EngineV2ActiveStrokeSurface {
    fun render(strokes: List<InkStroke>)
    fun commit(strokes: List<InkStroke>)
    fun cancel()
}
