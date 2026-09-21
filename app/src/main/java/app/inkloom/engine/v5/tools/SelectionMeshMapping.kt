package app.inkloom.engine.v5.tools

import kotlin.math.*

/** Interpolating lattice, tessellated once per draft. Inverse bins bound per-pixel work. */
class SelectionMeshMapping(val left: Float, val top: Float, val width: Float, val height: Float,
    val nodes: List<Pair<Float, Float>>, val columns: Int = 4, val rows: Int = 4) : SelectionRasterTransform {
    init {
        require(columns in 3..9 && rows in 3..9)
        require(nodes.size == columns * rows && nodes.all { it.first.isFinite() && it.second.isFinite() })
    }
    override val isIdentity = nodes.indices.all { i ->
        abs(nodes[i].first-(left+width*(i%columns)/(columns-1f))) < .0001f &&
            abs(nodes[i].second-(top+height*(i/columns)/(rows-1f))) < .0001f
    }
    private fun cubic(a: Float,b: Float,c: Float,d: Float,t: Float) =
        b + .5f*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)))
    private fun sample(u: Float,v: Float): Pair<Float,Float> {
        val gx=u.coerceIn(0f,1f)*(columns-1); val gy=v.coerceIn(0f,1f)*(rows-1)
        val ix=min(columns-2,gx.toInt()); val iy=min(rows-2,gy.toInt())
        fun component(horizontal: Boolean): Float {
            fun at(x: Int,y: Int): Float {
                // Linear extrapolation at the boundary keeps an undeformed lattice exactly affine.
                fun row(yy: Int): Float {
                    fun p(xx: Int) = nodes[yy*columns+xx].let { if(horizontal) it.first else it.second }
                    return when { x<0 -> 2*p(0)-p(1); x>=columns -> 2*p(columns-1)-p(columns-2); else -> p(x) }
                }
                return when { y<0 -> 2*row(0)-row(1); y>=rows -> 2*row(rows-1)-row(rows-2); else -> row(y) }
            }
            val r=FloatArray(4) { j -> cubic(at(ix-1,iy+j-1),at(ix,iy+j-1),at(ix+1,iy+j-1),at(ix+2,iy+j-1),gx-ix) }
            return cubic(r[0],r[1],r[2],r[3],gy-iy)
        }
        return component(true) to component(false)
    }
    override fun forward(x: Float,y: Float) = sample((x-left)/width,(y-top)/height)
    override fun inverseCoefficients(): FloatArray = error("Mesh uses a spatial inverse, not a homography")
    private val steps=max(24,max(columns-1,rows-1)*8)
    val vertices = (0..steps).flatMap { y -> (0..steps).map { x -> sample(x.toFloat()/steps,y.toFloat()/steps) } }
    val minX=vertices.minOf { it.first }; val maxX=vertices.maxOf { it.first }
    val minY=vertices.minOf { it.second }; val maxY=vertices.maxOf { it.second }
    private data class Triangle(val a:Int,val b:Int,val c:Int)
    private val triangles = (0 until steps).flatMap { y -> (0 until steps).flatMap { x ->
        val a=y*(steps+1)+x; listOf(Triangle(a,a+1,a+steps+2),Triangle(a,a+steps+2,a+steps+1))
    } }
    private class InverseTriangle(val triangle: Triangle, nodes: List<Pair<Float,Float>>, steps: Int,
        left: Float, top: Float, width: Float, height: Float) {
        private val a=nodes[triangle.a]; private val b=nodes[triangle.b]; private val c=nodes[triangle.c]
        private val determinant=(b.second-c.second).toDouble()*(a.first-c.first)+(c.first-b.first).toDouble()*(a.second-c.second)
        val valid=abs(determinant)>=1e-8f
        val ax=(b.second-c.second)/determinant; val ay=(c.first-b.first)/determinant
        val ac=-(ax*c.first+ay*c.second)
        val bx=(c.second-a.second)/determinant; val by=(a.first-c.first)/determinant
        val bc=-(bx*c.first+by*c.second)
        private val cx=(triangle.c%(steps+1)).toFloat(); private val cy=(triangle.c/(steps+1)).toFloat()
        private val ux=triangle.a%(steps+1)-cx; private val vx=triangle.b%(steps+1)-cx
        private val uy=triangle.a/(steps+1)-cy; private val vy=triangle.b/(steps+1)-cy
        val sx=width/steps*(ax*ux+bx*vx); val sy=width/steps*(ay*ux+by*vx)
        val sc=left+width/steps*(ac*ux+bc*vx+cx)
        val tx=height/steps*(ax*uy+bx*vy); val ty=height/steps*(ay*uy+by*vy)
        val tc=top+height/steps*(ac*uy+bc*vy+cy)
    }
    private val bins=Array(32*32) { mutableListOf<InverseTriangle>() }
    private fun bx(x: Float)=floor((x-minX)/max(1f,maxX-minX)*32).toInt().coerceIn(0,31)
    private fun by(y: Float)=floor((y-minY)/max(1f,maxY-minY)*32).toInt().coerceIn(0,31)
    init {
        triangles.forEach { t ->
            val inverse=InverseTriangle(t,vertices,steps,left,top,width,height)
            if(!inverse.valid) return@forEach
            val p=listOf(vertices[t.a],vertices[t.b],vertices[t.c])
            for(y in by(p.minOf { it.second })..by(p.maxOf { it.second }))
                for(x in bx(p.minOf { it.first })..bx(p.maxOf { it.first })) bins[y*32+x].add(inverse)
        }
    }
    /** Fixed source order gives deterministic frontmost triangles when the lattice folds. */
    fun inverse(x: Float,y: Float): Pair<Float,Float> {
        if(x<minX || x>maxX || y<minY || y>maxY) return -1e10f to -1e10f
        val candidates=bins[by(y)*32+bx(x)]
        for(i in candidates.indices.reversed()) {
            val t=candidates[i]
            val wa=t.ax*x+t.ay*y+t.ac
            val wb=t.bx*x+t.by*y+t.bc
            if(wa >= -1e-5f && wb >= -1e-5f && wa+wb <= 1.00001f) {
                return (t.sx*x+t.sy*y+t.sc).toFloat() to (t.tx*x+t.ty*y+t.tc).toFloat()
            }
        }
        return -1e10f to -1e10f
    }
}
