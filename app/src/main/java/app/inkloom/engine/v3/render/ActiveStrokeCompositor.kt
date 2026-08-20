package app.inkloom.engine.v3.render

internal interface ActiveStrokeCompositor {
    fun render(packet: StrokeRenderPacket)
    fun commit(packet: StrokeRenderPacket)
    /** Release matching transient ink only after the committed document frame was actually drawn. */
    fun onDocumentFramePresented(packetSequence: Long) = Unit
    fun cancel()

    /** True while the low-latency front buffer still holds uncommitted active ink. */
    val hasActiveInk: Boolean get() = false
}
