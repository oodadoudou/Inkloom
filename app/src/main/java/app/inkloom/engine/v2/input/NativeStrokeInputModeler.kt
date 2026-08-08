package app.inkloom.engine.v2.input

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JNI owner for the single Engine V2 input session. The direct result buffer is allocated once. */
internal class NativeStrokeInputModeler : AutoCloseable {
    private var handle: Long = nativeCreate().also { check(it != 0L) { "Unable to create Engine V2 input session" } }
    private val output = ByteBuffer.allocateDirect(OUTPUT_BYTES).order(ByteOrder.nativeOrder())

    val x: Float get() = output.getFloat(OFFSET_X)
    val y: Float get() = output.getFloat(OFFSET_Y)
    val pressure: Float get() = output.getFloat(OFFSET_PRESSURE)
    val timeMs: Long get() = output.getLong(OFFSET_TIME)
    val tilt: Float get() = output.getFloat(OFFSET_TILT)
    val orientation: Float get() = output.getFloat(OFFSET_ORIENTATION)
    val velocity: Float get() = output.getFloat(OFFSET_VELOCITY)

    fun reset() = nativeReset(requireHandle())

    fun append(
        rawX: Float,
        rawY: Float,
        adjustedPressure: Float,
        sampleTimeMs: Long,
        sampleTilt: Float,
        sampleOrientation: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        stabilization: Float,
        stabilizationBySpeed: Boolean,
        stabilizationBySpeedMode: Int,
        force: Boolean = false,
    ): Boolean = nativeAppend(
        requireHandle(), rawX, rawY, adjustedPressure, sampleTimeMs, sampleTilt,
        sampleOrientation, viewportWidthPx, viewportHeightPx, stabilization,
        stabilizationBySpeed, stabilizationBySpeedMode, force, output,
    )

    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeDestroy(current)
    }

    private fun requireHandle(): Long {
        if (handle == 0L) handle = nativeCreate().also { check(it != 0L) }
        return handle
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeReset(handle: Long)
    private external fun nativeAppend(
        handle: Long,
        rawX: Float,
        rawY: Float,
        adjustedPressure: Float,
        sampleTimeMs: Long,
        sampleTilt: Float,
        sampleOrientation: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        stabilization: Float,
        stabilizationBySpeed: Boolean,
        stabilizationBySpeedMode: Int,
        force: Boolean,
        output: ByteBuffer,
    ): Boolean

    private companion object {
        const val OUTPUT_BYTES = 40
        const val OFFSET_X = 0
        const val OFFSET_Y = 4
        const val OFFSET_PRESSURE = 8
        const val OFFSET_TIME = 16
        const val OFFSET_TILT = 24
        const val OFFSET_ORIENTATION = 28
        const val OFFSET_VELOCITY = 32

        init {
            System.loadLibrary("inkloom_engine_v2")
        }
    }
}
