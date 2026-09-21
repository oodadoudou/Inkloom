package app.inkloom.engine.v5.tools

import kotlin.math.*

enum class SelectionTransformMode { FREEFORM, UNIFORM, DISTORT, WARP, PUPPET }

enum class SelectionAlignment { LEFT, HORIZONTAL_CENTER, RIGHT, TOP, VERTICAL_CENTER, BOTTOM }

enum class SelectionTransformNode(val x: Float, val y: Float) {
    TOP_LEFT(0f, 0f), TOP(.5f, 0f), TOP_RIGHT(1f, 0f), RIGHT(1f, .5f),
    BOTTOM_RIGHT(1f, 1f), BOTTOM(.5f, 1f), BOTTOM_LEFT(0f, 1f), LEFT(0f, .5f),
    ROTATE(.5f, 0f), MOVE(.5f, .5f),
}

/** Oriented bounds in native raster pixels, retained across every gesture in one transform. */
data class SelectionTransformPose(
    val centerX: Float, val centerY: Float, val width: Float, val height: Float,
    val angle: Float = 0f, val flippedX: Boolean = false, val flippedY: Boolean = false,
    val corners: List<Pair<Float, Float>>? = null,
    val mesh: List<Pair<Float, Float>>? = null,
    val meshColumns: Int = 4,
    val meshRows: Int = 4,
) {
    init {
        require(listOf(centerX, centerY, width, height, angle).all(Float::isFinite))
        require(width > 0 && height > 0)
        require(meshColumns in 3..9 && meshRows in 3..9)
        require(mesh == null || mesh.size == meshColumns * meshRows)
    }

    fun point(x: Float, y: Float): Pair<Float, Float> {
        val delta = rotate((x - .5f) * width, (y - .5f) * height, angle)
        return centerX + delta.first to centerY + delta.second
    }

    fun affineFrom(original: SelectionTransformPose) = SelectionAffineTransform(
        original.centerX, original.centerY,
        width / original.width * if (flippedX) -1f else 1f,
        height / original.height * if (flippedY) -1f else 1f,
        angle, centerX - original.centerX, centerY - original.centerY,
    )

    fun sourceCorners(): List<Pair<Float, Float>> = corners ?: listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).map { (x,y) ->
        point(if (flippedX) 1f-x else x, if (flippedY) 1f-y else y)
    }

    fun rasterMappingFrom(original: SelectionTransformPose): SelectionRasterTransform =
        mesh?.let { SelectionMeshMapping(original.centerX-original.width/2, original.centerY-original.height/2, original.width, original.height, it, meshColumns, meshRows) } ?: corners?.let { SelectionProjectiveMapping(original.centerX-original.width/2, original.centerY-original.height/2,
            original.width, original.height, it) } ?: affineFrom(original)

    /** Affine controls after Distort operate on the retained original quad, never its raster preview. */
    fun withPose(next: SelectionTransformPose): SelectionTransformPose {
        val quad = mesh ?: corners ?: return next
        val mappedPoints = quad.map { (x,y) ->
            val local = rotate(x-centerX, y-centerY, -angle)
            val mapped = rotate(local.first*next.width/width * if (next.flippedX != flippedX) -1f else 1f,
                local.second*next.height/height * if (next.flippedY != flippedY) -1f else 1f, next.angle)
            next.centerX+mapped.first to next.centerY+mapped.second
        }
        return if (mesh != null) next.copy(mesh = mappedPoints, corners = null) else next.copy(corners = mappedPoints)
    }

    fun lattice(): List<Pair<Float, Float>> = mesh ?: (0 until meshRows).flatMap { y -> (0 until meshColumns).map { x ->
        val q = sourceCorners(); val u=x/(meshColumns-1f); val v=y/(meshRows-1f)
        ((1-u)*(1-v)*q[0].first+u*(1-v)*q[1].first+u*v*q[2].first+(1-u)*v*q[3].first) to
            ((1-u)*(1-v)*q[0].second+u*(1-v)*q[1].second+u*v*q[2].second+(1-u)*v*q[3].second)
    } }

    /** Changes mesh density without baking the current deformation into pixels. */
    fun withMeshDensity(columns: Int, rows: Int): SelectionTransformPose {
        val nextColumns = columns.coerceIn(3, 9)
        val nextRows = rows.coerceIn(3, 9)
        if (nextColumns == meshColumns && nextRows == meshRows) return copy(mesh = lattice())
        val old = mesh?.let {
            SelectionMeshMapping(
                0f,
                0f,
                1f,
                1f,
                it,
                meshColumns,
                meshRows,
            )
        }
        val q = sourceCorners()
        val sampled = (0 until nextRows).flatMap { y ->
            (0 until nextColumns).map { x ->
                val u = x / (nextColumns - 1f)
                val v = y / (nextRows - 1f)
                old?.forward(u, v)
                    ?: (((1-u)*(1-v)*q[0].first+u*(1-v)*q[1].first+u*v*q[2].first+(1-u)*v*q[3].first) to
                        ((1-u)*(1-v)*q[0].second+u*(1-v)*q[1].second+u*v*q[2].second+(1-u)*v*q[3].second))
            }
        }
        return copy(corners = null, mesh = sampled, meshColumns = nextColumns, meshRows = nextRows)
    }

    fun aligned(alignment: SelectionAlignment, referenceWidth: Float, referenceHeight: Float): SelectionTransformPose {
        val points = mesh ?: sourceCorners()
        val left = points.minOf { it.first }
        val right = points.maxOf { it.first }
        val top = points.minOf { it.second }
        val bottom = points.maxOf { it.second }
        val dx = when (alignment) {
            SelectionAlignment.LEFT -> -left
            SelectionAlignment.HORIZONTAL_CENTER -> referenceWidth / 2f - (left + right) / 2f
            SelectionAlignment.RIGHT -> referenceWidth - right
            else -> 0f
        }
        val dy = when (alignment) {
            SelectionAlignment.TOP -> -top
            SelectionAlignment.VERTICAL_CENTER -> referenceHeight / 2f - (top + bottom) / 2f
            SelectionAlignment.BOTTOM -> referenceHeight - bottom
            else -> 0f
        }
        return withPose(copy(centerX = centerX + dx, centerY = centerY + dy))
    }

    fun warp(index: Int, dx: Float, dy: Float): SelectionTransformPose {
        val points=lattice().mapIndexed { i,p -> if(i==index) p.first+dx to p.second+dy else p }
        val l=points.minOf { it.first }; val r=points.maxOf { it.first }; val t=points.minOf { it.second }; val b=points.maxOf { it.second }
        return copy(centerX=(l+r)/2,centerY=(t+b)/2,width=(r-l).coerceAtLeast(1f),height=(b-t).coerceAtLeast(1f),angle=0f,corners=null,mesh=points)
    }

    /** Moves a puppet pin with smooth radial falloff while other pins anchor their nodes. */
    fun puppetWarp(index: Int, dx: Float, dy: Float, anchored: Set<Int>): SelectionTransformPose {
        val source = lattice()
        val pivot = source.getOrNull(index) ?: return this
        val radius = max(width, height).coerceAtLeast(1f) * .42f
        val points = source.mapIndexed { node, point ->
            when {
                node in anchored -> point
                node == index -> point.first + dx to point.second + dy
                else -> {
                    val distance = hypot(point.first - pivot.first, point.second - pivot.second)
                    val normalized = (1f - distance / radius).coerceIn(0f, 1f)
                    val weight = normalized * normalized * (3f - 2f * normalized)
                    point.first + dx * weight to point.second + dy * weight
                }
            }
        }
        val left=points.minOf { it.first }; val right=points.maxOf { it.first }
        val top=points.minOf { it.second }; val bottom=points.maxOf { it.second }
        return copy(centerX=(left+right)/2, centerY=(top+bottom)/2, width=(right-left).coerceAtLeast(1f),
            height=(bottom-top).coerceAtLeast(1f), angle=0f, corners=null, mesh=points)
    }

    fun distort(node: SelectionTransformNode, dx: Float, dy: Float): SelectionTransformPose? {
        if (dx == 0f && dy == 0f) return this
        val indices = when (node) {
            SelectionTransformNode.TOP_LEFT -> setOf(0); SelectionTransformNode.TOP -> setOf(0,1)
            SelectionTransformNode.TOP_RIGHT -> setOf(1); SelectionTransformNode.RIGHT -> setOf(1,2)
            SelectionTransformNode.BOTTOM_RIGHT -> setOf(2); SelectionTransformNode.BOTTOM -> setOf(2,3)
            SelectionTransformNode.BOTTOM_LEFT -> setOf(3); SelectionTransformNode.LEFT -> setOf(3,0)
            else -> setOf(0,1,2,3)
        }
        val next = sourceCorners().mapIndexed { i,p -> if (i in indices) p.first+dx to p.second+dy else p }
        if (!SelectionProjectiveMapping.validCorners(next)) return null
        val left=next.minOf { it.first }; val right=next.maxOf { it.first }
        val top=next.minOf { it.second }; val bottom=next.maxOf { it.second }
        return SelectionTransformPose((left+right)/2, (top+bottom)/2, right-left, bottom-top, corners=next)
    }

    fun drag(node: SelectionTransformNode, startX: Float, startY: Float, x: Float, y: Float,
             mode: SelectionTransformMode): SelectionTransformPose {
        if (mode == SelectionTransformMode.DISTORT) return distort(node, x-startX, y-startY) ?: this
        if (node == SelectionTransformNode.MOVE) return withPose(copy(centerX = centerX + x - startX, centerY = centerY + y - startY))
        if (node == SelectionTransformNode.ROTATE) {
            val delta = atan2(y - centerY, x - centerX) - atan2(startY - centerY, startX - centerX)
            return withPose(copy(angle = (angle + Math.toDegrees(delta.toDouble()).toFloat()).mod(360f)))
        }
        val delta = rotate(x - startX, y - startY, -angle)
        val sx = (node.x - .5f) * 2f
        val sy = (node.y - .5f) * 2f
        var nextWidth = if (sx == 0f) width else (width + delta.first * sx).coerceAtLeast(1f)
        var nextHeight = if (sy == 0f) height else (height + delta.second * sy).coerceAtLeast(1f)
        if (mode == SelectionTransformMode.UNIFORM) {
            val scale = when {
                sx == 0f -> nextHeight / height
                sy == 0f -> nextWidth / width
                else -> 1f + (delta.first * sx * width + delta.second * sy * height) / (width * width + height * height)
            }.coerceAtLeast(max(1f / width, 1f / height))
            nextWidth = width * scale; nextHeight = height * scale
        }
        val anchor = point(1f - node.x, 1f - node.y)
        val offset = rotate((.5f - node.x) * nextWidth, (.5f - node.y) * nextHeight, angle)
        return withPose(copy(centerX = anchor.first - offset.first, centerY = anchor.second - offset.second,
            width = nextWidth, height = nextHeight))
    }

    companion object {
        fun rotate(x: Float, y: Float, angle: Float): Pair<Float, Float> {
            val rad = Math.toRadians(angle.toDouble())
            return (x * cos(rad) - y * sin(rad)).toFloat() to (x * sin(rad) + y * cos(rad)).toFloat()
        }
    }
}
