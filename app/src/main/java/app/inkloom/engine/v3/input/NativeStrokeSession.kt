package app.inkloom.engine.v3.input

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JNI owner for one Engine V3 paper-trace session. Both direct buffers are allocated once. */
internal class NativeStrokeSession : AutoCloseable {
    private var handle: Long = nativeCreate().also { check(it != 0L) { "Unable to create Engine V3 input session" } }
    private val input = ByteBuffer.allocateDirect(MAX_BATCH_SAMPLES * INPUT_STRIDE_BYTES).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(MAX_BATCH_SAMPLES * OUTPUT_STRIDE_BYTES).order(ByteOrder.nativeOrder())

    val x: Float get() = x(0)
    val y: Float get() = y(0)
    val pressure: Float get() = pressure(0)
    val timeMs: Long get() = timeMs(0)
    val tilt: Float get() = tilt(0)
    val orientation: Float get() = orientation(0)
    val velocity: Float get() = velocity(0)
    val batchCapacity: Int get() = MAX_BATCH_SAMPLES

    fun x(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_X))
    fun y(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_Y))
    fun pressure(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_PRESSURE))
    fun timeMs(index: Int): Long = output.getLong(outputOffset(index, OFFSET_TIME))
    fun tilt(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_TILT))
    fun orientation(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_ORIENTATION))
    fun velocity(index: Int): Float = output.getFloat(outputOffset(index, OFFSET_VELOCITY))

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
        streamLine: Float = 0f,
        streamLinePressure: Float = 0f,
        procreateStabilization: Float = 0f,
        motionFiltering: Float = 0f,
        motionFilteringExpression: Float = 0f,
        force: Boolean = false,
    ): Boolean {
        putSample(0, rawX, rawY, adjustedPressure, sampleTimeMs, sampleTilt, sampleOrientation, force)
        return appendBatch(1, viewportWidthPx, viewportHeightPx, stabilization, stabilizationBySpeed, stabilizationBySpeedMode, streamLine, streamLinePressure, procreateStabilization, motionFiltering, motionFilteringExpression) == 1
    }

    /** Stages one raw input in the preallocated direct buffer. No object crosses JNI. */
    fun putSample(
        index: Int,
        rawX: Float,
        rawY: Float,
        adjustedPressure: Float,
        sampleTimeMs: Long,
        sampleTilt: Float,
        sampleOrientation: Float,
        force: Boolean = false,
    ) {
        require(index in 0 until MAX_BATCH_SAMPLES)
        val offset = index * INPUT_STRIDE_BYTES
        input.putFloat(offset + INPUT_OFFSET_X, rawX)
        input.putFloat(offset + INPUT_OFFSET_Y, rawY)
        input.putFloat(offset + INPUT_OFFSET_PRESSURE, adjustedPressure)
        input.putLong(offset + INPUT_OFFSET_TIME, sampleTimeMs)
        input.putFloat(offset + INPUT_OFFSET_TILT, sampleTilt)
        input.putFloat(offset + INPUT_OFFSET_ORIENTATION, sampleOrientation)
        input.putInt(offset + INPUT_OFFSET_FLAGS, if (force) INPUT_FLAG_FORCE else 0)
    }

    /** Models every staged historical/current sample with one coarse-grained JNI call. */
    fun appendBatch(
        sampleCount: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        stabilization: Float,
        stabilizationBySpeed: Boolean,
        stabilizationBySpeedMode: Int,
        streamLine: Float = 0f,
        streamLinePressure: Float = 0f,
        procreateStabilization: Float = 0f,
        motionFiltering: Float = 0f,
        motionFilteringExpression: Float = 0f,
    ): Int {
        if (sampleCount <= 0) return 0
        require(sampleCount <= MAX_BATCH_SAMPLES)
        return nativeAppendBatch(
            requireHandle(), input, sampleCount, viewportWidthPx, viewportHeightPx,
            stabilization, stabilizationBySpeed, stabilizationBySpeedMode,
            streamLine, streamLinePressure, procreateStabilization, motionFiltering, motionFilteringExpression,
            output,
        )
    }

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
    private external fun nativeAppendBatch(
        handle: Long,
        input: ByteBuffer,
        sampleCount: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        stabilization: Float,
        stabilizationBySpeed: Boolean,
        stabilizationBySpeedMode: Int,
        streamLine: Float,
        streamLinePressure: Float,
        procreateStabilization: Float,
        motionFiltering: Float,
        motionFilteringExpression: Float,
        output: ByteBuffer,
    ): Int

    private companion object {
        const val MAX_BATCH_SAMPLES = 256
        const val INPUT_STRIDE_BYTES = 32
        const val INPUT_OFFSET_X = 0
        const val INPUT_OFFSET_Y = 4
        const val INPUT_OFFSET_PRESSURE = 8
        const val INPUT_OFFSET_TIME = 16
        const val INPUT_OFFSET_TILT = 24
        const val INPUT_OFFSET_ORIENTATION = 28
        const val INPUT_OFFSET_FLAGS = 12
        const val INPUT_FLAG_FORCE = 1
        const val OUTPUT_STRIDE_BYTES = 40
        const val OFFSET_X = 0
        const val OFFSET_Y = 4
        const val OFFSET_PRESSURE = 8
        const val OFFSET_TIME = 16
        const val OFFSET_TILT = 24
        const val OFFSET_ORIENTATION = 28
        const val OFFSET_VELOCITY = 32

        init {
            System.loadLibrary("inkloom_engine_v3")
        }

        fun outputOffset(index: Int, fieldOffset: Int): Int {
            require(index in 0 until MAX_BATCH_SAMPLES)
            return index * OUTPUT_STRIDE_BYTES + fieldOffset
        }
    }
}
