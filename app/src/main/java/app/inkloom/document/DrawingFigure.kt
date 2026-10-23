package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The joints of the drawing figure, each turned relative to its parent. */
@Serializable
enum class FigureJoint(val parent: FigureJoint?, val length: Float, val radius: Float, val rest: Vec3) {
    PELVIS(null, 0f, .11f, Vec3(0f, 1f, 0f)),
    SPINE(PELVIS, .22f, .10f, Vec3(0f, 1f, 0f)),
    CHEST(SPINE, .22f, .13f, Vec3(0f, 1f, 0f)),
    NECK(CHEST, .1f, .045f, Vec3(0f, 1f, 0f)),
    HEAD(NECK, .2f, .11f, Vec3(0f, 1f, 0f)),
    LEFT_SHOULDER(CHEST, .17f, .05f, Vec3(1f, -.15f, 0f)),
    LEFT_UPPER_ARM(LEFT_SHOULDER, .3f, .05f, Vec3(0f, -1f, 0f)),
    LEFT_FOREARM(LEFT_UPPER_ARM, .27f, .042f, Vec3(0f, -1f, 0f)),
    LEFT_HAND(LEFT_FOREARM, .09f, .04f, Vec3(0f, -1f, 0f)),
    RIGHT_SHOULDER(CHEST, .17f, .05f, Vec3(-1f, -.15f, 0f)),
    RIGHT_UPPER_ARM(RIGHT_SHOULDER, .3f, .05f, Vec3(0f, -1f, 0f)),
    RIGHT_FOREARM(RIGHT_UPPER_ARM, .27f, .042f, Vec3(0f, -1f, 0f)),
    RIGHT_HAND(RIGHT_FOREARM, .09f, .04f, Vec3(0f, -1f, 0f)),
    LEFT_HIP(PELVIS, .1f, .07f, Vec3(1f, -.3f, 0f)),
    LEFT_THIGH(LEFT_HIP, .44f, .075f, Vec3(0f, -1f, 0f)),
    LEFT_SHIN(LEFT_THIGH, .42f, .055f, Vec3(0f, -1f, 0f)),
    LEFT_FOOT(LEFT_SHIN, .16f, .045f, Vec3(0f, -.25f, 1f)),
    RIGHT_HIP(PELVIS, .1f, .07f, Vec3(-1f, -.3f, 0f)),
    RIGHT_THIGH(RIGHT_HIP, .44f, .075f, Vec3(0f, -1f, 0f)),
    RIGHT_SHIN(RIGHT_THIGH, .42f, .055f, Vec3(0f, -1f, 0f)),
    RIGHT_FOOT(RIGHT_SHIN, .16f, .045f, Vec3(0f, -.25f, 1f)),
}

@Serializable
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(dot(this))
    fun normalized() = length.let { if (it < 1e-6f) this else this * (1f / it) }
}

/** A joint's turn in degrees: bend forward/back (x), twist (y), raise sideways (z). */
@Serializable
data class JointTurn(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

/** How the figure is drawn into the layer. */
@Serializable
enum class FigureStyle { SHADED, OUTLINE, SILHOUETTE }

/**
 * LM-36's 3D drawing figure: a posable mannequin with its camera. [turns] are per joint; the camera
 * orbits the figure ([yaw], [pitch] degrees) at [distance] figure heights, with a [fov] lens.
 */
@Serializable
data class DrawingFigure(
    val turns: Map<FigureJoint, JointTurn> = emptyMap(),
    val yaw: Float = 25f,
    val pitch: Float = 8f,
    val distance: Float = 3.2f,
    val fov: Float = 40f,
    /** Where the figure stands on the page (fractions) and how tall it is (fraction of the page height). */
    val centerX: Float = .5f,
    val centerY: Float = .55f,
    val height: Float = .8f,
    val headScale: Float = 1f,
    val shoulderWidth: Float = 1f,
    val style: FigureStyle = FigureStyle.SHADED,
    val colorArgb: Int = 0xFFD8D2CC.toInt(),
    val lineArgb: Int = 0xFF303030.toInt(),
) {
    fun turn(joint: FigureJoint) = turns[joint] ?: JointTurn()
    fun withTurn(joint: FigureJoint, turn: JointTurn) = copy(turns = turns + (joint to turn))

    companion object {
        /** Starting poses, like CSP's pose materials. */
        fun pose(name: FigurePose): DrawingFigure = DrawingFigure(turns = name.turns)
    }
}

@Serializable
enum class FigurePose(val turns: Map<FigureJoint, JointTurn>) {
    STANDING(mapOf(FigureJoint.LEFT_UPPER_ARM to JointTurn(z = 8f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(z = -8f))),
    T_POSE(mapOf(FigureJoint.LEFT_UPPER_ARM to JointTurn(z = 90f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(z = -90f))),
    WALKING(mapOf(FigureJoint.LEFT_THIGH to JointTurn(x = -25f), FigureJoint.LEFT_SHIN to JointTurn(x = 10f),
        FigureJoint.RIGHT_THIGH to JointTurn(x = 20f), FigureJoint.RIGHT_SHIN to JointTurn(x = 25f),
        FigureJoint.LEFT_UPPER_ARM to JointTurn(x = 20f, z = 6f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(x = -20f, z = -6f),
        FigureJoint.LEFT_FOREARM to JointTurn(x = -15f), FigureJoint.RIGHT_FOREARM to JointTurn(x = -25f))),
    RUNNING(mapOf(FigureJoint.SPINE to JointTurn(x = -12f), FigureJoint.LEFT_THIGH to JointTurn(x = -55f), FigureJoint.LEFT_SHIN to JointTurn(x = 70f),
        FigureJoint.RIGHT_THIGH to JointTurn(x = 35f), FigureJoint.RIGHT_SHIN to JointTurn(x = 40f),
        FigureJoint.LEFT_UPPER_ARM to JointTurn(x = 45f, z = 10f), FigureJoint.LEFT_FOREARM to JointTurn(x = -80f),
        FigureJoint.RIGHT_UPPER_ARM to JointTurn(x = -40f, z = -10f), FigureJoint.RIGHT_FOREARM to JointTurn(x = -80f))),
    SITTING(mapOf(FigureJoint.LEFT_THIGH to JointTurn(x = -90f), FigureJoint.RIGHT_THIGH to JointTurn(x = -90f),
        FigureJoint.LEFT_SHIN to JointTurn(x = 90f), FigureJoint.RIGHT_SHIN to JointTurn(x = 90f),
        FigureJoint.LEFT_UPPER_ARM to JointTurn(x = -20f, z = 10f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(x = -20f, z = -10f),
        FigureJoint.LEFT_FOREARM to JointTurn(x = -50f), FigureJoint.RIGHT_FOREARM to JointTurn(x = -50f))),
    ARMS_UP(mapOf(FigureJoint.LEFT_UPPER_ARM to JointTurn(z = 160f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(z = -160f))),
    THINKING(mapOf(FigureJoint.HEAD to JointTurn(x = 12f, y = -10f), FigureJoint.RIGHT_UPPER_ARM to JointTurn(x = -30f, z = -15f),
        FigureJoint.RIGHT_FOREARM to JointTurn(x = -140f), FigureJoint.LEFT_UPPER_ARM to JointTurn(x = -15f, z = 20f), FigureJoint.LEFT_FOREARM to JointTurn(x = -90f, y = 60f))),
}

/** A bone segment in world space after posing. */
data class FigureBone(val joint: FigureJoint, val start: Vec3, val end: Vec3, val radius: Float)

/** Forward kinematics and the camera's projection onto the page. */
object FigureRig {
    /** World-space bones of [figure], 1 unit = the figure's height ≈ 1.75. */
    fun bones(figure: DrawingFigure): List<FigureBone> {
        val frames = HashMap<FigureJoint, Pair<Vec3, Mat3>>()
        val out = ArrayList<FigureBone>()
        FigureJoint.entries.forEach { joint ->
            val (origin, parentRotation) = joint.parent?.let { frames.getValue(it) } ?: (Vec3(0f, .95f, 0f) to Mat3.IDENTITY)
            val t = figure.turn(joint)
            val rotation = parentRotation * Mat3.euler(t.x, t.y, t.z)
            val scale = when (joint) {
                FigureJoint.HEAD -> figure.headScale
                FigureJoint.LEFT_SHOULDER, FigureJoint.RIGHT_SHOULDER -> figure.shoulderWidth
                else -> 1f
            }
            val end = origin + rotation.apply(joint.rest.normalized()) * (joint.length * scale)
            val radius = joint.radius * if (joint == FigureJoint.HEAD) figure.headScale else 1f
            out += FigureBone(joint, origin, end, radius)
            frames[joint] = end to rotation
        }
        return out
    }

    /** The camera: world point → page pixels (x, y) and depth; null behind the camera. */
    class Camera(figure: DrawingFigure, private val width: Float, private val height: Float) {
        private val target = Vec3(0f, .9f, 0f)
        val eye: Vec3
        private val forward: Vec3; private val right: Vec3; private val up: Vec3
        private val focal: Float
        private val scale: Float
        init {
            val yaw = figure.yaw * PI.toFloat() / 180f; val pitch = figure.pitch * PI.toFloat() / 180f
            eye = target + Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)) * figure.distance
            forward = (target - eye).normalized()
            right = forward.cross(Vec3(0f, 1f, 0f)).normalized().let { if (it.length < .5f) Vec3(1f, 0f, 0f) else it }
            up = right.cross(forward).normalized()
            focal = 1f / tan(figure.fov * PI.toFloat() / 360f)
            // The figure's 1.8 units of height fill [height] of the page at the target's distance.
            scale = figure.height * height / (1.8f * focal / figure.distance)
        }
        private val cx = figure.centerX * width; private val cy = figure.centerY * height

        fun project(p: Vec3): Triple<Float, Float, Float>? {
            val d = p - eye
            val z = d.dot(forward)
            if (z <= .01f) return null
            return Triple(cx + d.dot(right) * focal / z * scale, cy - d.dot(up) * focal / z * scale, z)
        }

        /** Page pixels per world unit at depth [z]. */
        fun pixelsPerUnit(z: Float) = focal / z.coerceAtLeast(.01f) * scale

        /** The viewing ray through page pixel (x, y), as origin and direction. */
        fun ray(x: Float, y: Float): Pair<Vec3, Vec3> {
            val u = (x - cx) / (focal * scale); val v = -(y - cy) / (focal * scale)
            return eye to (forward + right * u + up * v).normalized()
        }

        /** Vanishing point of a world direction, in page fractions; null when it is parallel to the picture. */
        fun vanishingPoint(direction: Vec3): Pair<Float, Float>? {
            val d = direction.normalized(); val z = d.dot(forward)
            if (abs(z) < 1e-3f) return null
            return (cx + d.dot(right) * focal / z * scale) / width to (cy - d.dot(up) * focal / z * scale) / height
        }
    }

    /** A perspective ruler matching the camera: vanishing points of the figure's left–right, front–back and up axes. */
    fun perspective(figure: DrawingFigure, width: Float, height: Float): PerspectiveRuler? {
        val camera = Camera(figure, width, height)
        val points = listOf(Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 1f, 0f)).mapNotNull { axis ->
            (camera.vanishingPoint(axis) ?: camera.vanishingPoint(axis * -1f))?.takeIf { (x, y) -> abs(x) < 20f && abs(y) < 20f }
        }
        if (points.isEmpty()) return null
        return PerspectiveRuler(points.take(3).flatMap { listOf(it.first, it.second) })
    }
}

/** A row-major 3 × 3 rotation. */
data class Mat3(val m: FloatArray) {
    operator fun times(o: Mat3) = Mat3(FloatArray(9) { i -> val r = i / 3; val c = i % 3; (0..2).sumOf { k -> (m[r * 3 + k] * o.m[k * 3 + c]).toDouble() }.toFloat() })
    fun apply(v: Vec3) = Vec3(m[0] * v.x + m[1] * v.y + m[2] * v.z, m[3] * v.x + m[4] * v.y + m[5] * v.z, m[6] * v.x + m[7] * v.y + m[8] * v.z)
    override fun equals(other: Any?) = other is Mat3 && other.m.contentEquals(m)
    override fun hashCode() = m.contentHashCode()

    companion object {
        val IDENTITY = Mat3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        /** Turns about x, then y, then z, in degrees. */
        fun euler(x: Float, y: Float, z: Float): Mat3 {
            fun r(a: Float) = a * PI.toFloat() / 180f
            val cx = cos(r(x)); val sx = sin(r(x)); val cy = cos(r(y)); val sy = sin(r(y)); val cz = cos(r(z)); val sz = sin(r(z))
            val rx = Mat3(floatArrayOf(1f, 0f, 0f, 0f, cx, -sx, 0f, sx, cx))
            val ry = Mat3(floatArrayOf(cy, 0f, sy, 0f, 1f, 0f, -sy, 0f, cy))
            val rz = Mat3(floatArrayOf(cz, -sz, 0f, sz, cz, 0f, 0f, 0f, 1f))
            return rz * ry * rx
        }
    }
}
