package app.inkloom.engine.v5.tools

import kotlin.math.*

/** Interpolating lattice, tessellated once per draft. Inverse bins bound per-pixel work. */
class SelectionMeshMapping(val left: Float, val top: Float, val width: Float, val height: Float,
    val nodes: List<Pair<Float, Float>>) : SelectionRasterTransform {
    init { require(nodes.size == 16 && nodes.all { it.first.isFinite() && it.second.isFinite() }) }
    override val isIdentity = nodes.indices.all { i ->
        abs(nodes[i].first-(left+width*(i%4)/3)) < .0001f && abs(nodes[i].second-(top+height*(i/4)/3)) < .0001f
    }
    private fun cubic(a: Float,b: Float,c: Float,d: Float,t: Float) =
        b + .5f*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)))
    private fun sample(u: Float,v: Float): Pair<Float,Float> {
        val gx=u.coerceIn(0f,1f)*3; val gy=v.coerceIn(0f,1f)*3
        val ix=min(2,gx.toInt()); val iy=min(2,gy.toInt())
        fun component(horizontal: Boolean): Float {
            fun at(x: Int,y: Int): Float {
                // Linear extrapolation at the boundary keeps an undeformed lattice exactly affine.
                fun row(yy: Int): Float {
                    fun p(xx: Int) = nodes[yy*4+xx].let { if(horizontal) it.first else it.second }
                    return when { x<0 -> 2*p(0)-p(1); x>3 -> 2*p(3)-p(2); else -> p(x) }
                }
                return when { y<0 -> 2*row(0)-row(1); y>3 -> 2*row(3)-row(2); else -> row(y) }
            }
            val r=FloatArray(4) { j -> cubic(at(ix-1,iy+j-1),at(ix,iy+j-1),at(ix+1,iy+j-1),at(ix+2,iy+j-1),gx-ix) }
            return cubic(r[0],r[1],r[2],r[3],gy-iy)
        }
        return component(true) to component(false)
    }
    override fun forward(x: Float,y: Float) = sample((x-left)/width,(y-top)/height)
    override fun inverseCoefficients(): FloatArray = error("Mesh uses a spatial inverse, not a homography")
    private val steps=24
    val vertices = (0..steps).flatMap { y -> (0..steps).map { x -> sample(x.toFloat()/steps,y.toFloat()/steps) } }
    val minX=vertices.minOf { it.first }; val maxX=vertices.maxOf { it.first }
    val minY=vertices.minOf { it.second }; val maxY=vertices.maxOf { it.second }
    private data class Triangle(val a:Int,val b:Int,val c:Int)
    private val triangles = (0 until steps).flatMap { y -> (0 until steps).flatMap { x ->
        val a=y*(steps+1)+x; listOf(Triangle(a,a+1,a+steps+2),Triangle(a,a+steps+2,a+steps+1))
    } }
    private val bins=Array(32*32) { mutableListOf<Triangle>() }
    private fun bx(x: Float)=floor((x-minX)/max(1f,maxX-minX)*32).toInt().coerceIn(0,31)
    private fun by(y: Float)=floor((y-minY)/max(1f,maxY-minY)*32).toInt().coerceIn(0,31)
    init {
        triangles.forEach { t ->
            val p=listOf(vertices[t.a],vertices[t.b],vertices[t.c])
            for(y in by(p.minOf { it.second })..by(p.maxOf { it.second }))
                for(x in bx(p.minOf { it.first })..bx(p.maxOf { it.first })) bins[y*32+x].add(t)
        }
    }
    /** Fixed source order gives deterministic frontmost triangles when the lattice folds. */
    fun inverse(x: Float,y: Float): Pair<Float,Float> {
        if(x<minX || x>maxX || y<minY || y>maxY) return -1e10f to -1e10f
        val candidates=bins[by(y)*32+bx(x)]
        for(i in candidates.indices.reversed()) {
            val t=candidates[i]; val a=vertices[t.a]; val b=vertices[t.b]; val c=vertices[t.c]
            val det=(b.second-c.second)*(a.first-c.first)+(c.first-b.first)*(a.second-c.second)
            if(abs(det)<1e-8f) continue
            val wa=((b.second-c.second)*(x-c.first)+(c.first-b.first)*(y-c.second))/det
            val wb=((c.second-a.second)*(x-c.first)+(a.first-c.first)*(y-c.second))/det
            val wc=1-wa-wb
            if(wa >= -1e-5f && wb >= -1e-5f && wc >= -1e-5f) {
                return left+width*(wa*(t.a%(steps+1))+wb*(t.b%(steps+1))+wc*(t.c%(steps+1)))/steps to
                    top+height*(wa*(t.a/(steps+1))+wb*(t.b/(steps+1))+wc*(t.c/(steps+1)))/steps
            }
        }
        return -1e10f to -1e10f
    }
}
