package app.inkloom.brushpresetv5

import app.inkloom.document.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Built-in Scratch Pad test trajectories. The Scratch Pad renders each of these through the
 * V5 input path, so adjusting a pressure curve or tilt mapping is visible immediately without
 * hand-drawing every time. The trajectories contain input samples, never legacy stroke objects.
 */
object ScratchPadTrajectories {

    /** A straight line at constant pressure and upright tilt (baseline). */
    fun straight(
        pressure: Float = .6f,
        tilt: Float = 0f,
        samples: Int = 300,
        timeStepMs: Long = 2L,
    ): List<StrokePoint> =
        (0..samples).map { i ->
            StrokePoint(
                x = .1f + i * .0026f,
                y = .5f,
                pressure = pressure,
                timeMs = (i * timeStepMs).toLong(),
                tilt = tilt,
            )
        }

    /** Pressure ramp thin -> thick (pressure scales size/opacity when the channels are enabled). */
    fun pressureRamp(samples: Int = 300): List<StrokePoint> =
        (0..samples).map { i ->
            val t = i / samples.toFloat()
            StrokePoint(
                x = .1f + t * .8f,
                y = .35f,
                pressure = t,
                timeMs = (i * 2L).toLong(),
            )
        }

    /** Tilt ramp upright -> tilted (exercises tilt->width and tilt->grain). */
    fun tiltRamp(samples: Int = 300): List<StrokePoint> =
        (0..samples).map { i ->
            val t = i / samples.toFloat()
            StrokePoint(
                x = .1f + t * .8f,
                y = .5f,
                pressure = .7f,
                timeMs = (i * 2L).toLong(),
                tilt = t * 1.2f,
            )
        }

    /** A circle at constant pressure (turns / curvature / joins). */
    fun circle(radius: Float = .28f, samples: Int = 360): List<StrokePoint> =
        (0..samples).map { i ->
            val angle = i / samples.toFloat() * 2f * PI.toFloat()
            StrokePoint(
                x = .5f + radius * cos(angle),
                y = .5f + radius * sin(angle),
                pressure = .6f,
                timeMs = (i * 2L).toLong(),
            )
        }

    /** An S-curve (stroke direction changes / scalloping). */
    fun sCurve(samples: Int = 400): List<StrokePoint> =
        (0..samples).map { i ->
            val t = i / samples.toFloat()
            StrokePoint(
                x = .1f + t * .8f,
                y = .5f + .18f * sin(t * (PI.toFloat() * 3f)),
                pressure = .6f,
                timeMs = (i * 2L).toLong(),
            )
        }

    val all = listOf("straight", "pressureRamp", "tiltRamp", "circle", "sCurve")

    fun byName(name: String): List<StrokePoint> = when (name) {
        "straight" -> straight()
        "pressureRamp" -> pressureRamp()
        "tiltRamp" -> tiltRamp()
        "circle" -> circle()
        "sCurve" -> sCurve()
        else -> straight()
    }
}
