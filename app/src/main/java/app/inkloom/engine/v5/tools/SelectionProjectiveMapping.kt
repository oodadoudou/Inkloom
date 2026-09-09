package app.inkloom.engine.v5.tools

import kotlin.math.abs

/** Native raster coordinates, with the same inverse used for selected pixels and mask alpha. */
interface SelectionRasterTransform {
    val isIdentity: Boolean
    fun forward(x: Float, y: Float): Pair<Float, Float>
    fun inverseCoefficients(): FloatArray
}

/** Maps one rectangle to a convex quadrilateral; no intermediate raster is resampled. */
class SelectionProjectiveMapping(
    left: Float, top: Float, width: Float, height: Float,
    corners: List<Pair<Float, Float>>,
) : SelectionRasterTransform {
    override val isIdentity = corners == listOf(left to top, left+width to top, left+width to top+height, left to top+height)
    private val matrix: DoubleArray
    private val inverse: FloatArray
    init {
        require(listOf(left, top, width, height).all(Float::isFinite) && width > 0 && height > 0)
        require(validCorners(corners)) { "Keep the four corners convex and apart" }
        // Solve source-normalized square -> quad in double precision, then compose pixel units.
        val rows = Array(8) { DoubleArray(9) }
        val uv = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        for (i in 0..3) {
            val (u, v) = uv[i]; val x = corners[i].first.toDouble(); val y = corners[i].second.toDouble()
            rows[i*2] = doubleArrayOf(u,v,1.0,0.0,0.0,0.0,-x*u,-x*v,x)
            rows[i*2+1] = doubleArrayOf(0.0,0.0,0.0,u,v,1.0,-y*u,-y*v,y)
        }
        for (col in 0..7) {
            val pivot = (col..7).maxBy { abs(rows[it][col]) }
            require(abs(rows[pivot][col]) > 1e-10) { "Degenerate perspective" }
            val swap = rows[col]; rows[col] = rows[pivot]; rows[pivot] = swap
            val factor = rows[col][col]
            for (j in col..8) rows[col][j] /= factor
            for (i in 0..7) if (i != col) {
                val value = rows[i][col]
                for (j in col..8) rows[i][j] -= value * rows[col][j]
            }
        }
        val q = DoubleArray(9) { if (it == 8) 1.0 else rows[it][8] }
        matrix = doubleArrayOf(q[0]/width, q[1]/height, q[2]-q[0]*left/width-q[1]*top/height,
            q[3]/width, q[4]/height, q[5]-q[3]*left/width-q[4]*top/height,
            q[6]/width, q[7]/height, 1.0-q[6]*left/width-q[7]*top/height)
        val a=matrix[0]; val b=matrix[1]; val c=matrix[2]; val d=matrix[3]; val e=matrix[4]; val f=matrix[5]
        val g=matrix[6]; val h=matrix[7]; val i=matrix[8]
        val det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g)
        require(abs(det) > 1e-14)
        // Six affine coefficients followed by the homogeneous denominator.
        inverse = doubleArrayOf(e*i-f*h,c*h-b*i,f*g-d*i,a*i-c*g,b*f-c*e,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d)
            .map { (it/det).toFloat() }.toFloatArray()
        require(inverse.all(Float::isFinite))
    }
    override fun forward(x: Float, y: Float): Pair<Float, Float> {
        val w=matrix[6]*x+matrix[7]*y+matrix[8]
        return ((matrix[0]*x+matrix[1]*y+matrix[2])/w).toFloat() to ((matrix[3]*x+matrix[4]*y+matrix[5])/w).toFloat()
    }
    override fun inverseCoefficients() = inverse.copyOf()

    internal fun crossesHorizon(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val values = listOf(left to top, right to top, right to bottom, left to bottom)
            .map { (x,y) -> matrix[6]*x+matrix[7]*y+matrix[8] }
        return values.min() <= 0.0 && values.max() >= 0.0
    }

    companion object {
        fun validCorners(points: List<Pair<Float, Float>>): Boolean {
            if (points.size != 4 || points.any { !it.first.isFinite() || !it.second.isFinite() }) return false
            val cross = (0..3).map { n ->
                val a=points[n]; val b=points[(n+1)%4]; val c=points[(n+2)%4]
                (b.first-a.first).toDouble()*(c.second-b.second)-(b.second-a.second).toDouble()*(c.first-b.first)
            }
            return cross.all { it >= 1.0 } || cross.all { it <= -1.0 }
        }
    }
}
