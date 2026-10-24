package app.inkloom.color

import app.inkloom.engine.v5.surface.*
import java.io.File
import kotlin.math.pow

internal class IccColorPipeline(private val settings: PageColorManagement, preview: Boolean = false,
    cmykProfile: ByteArray? = null) : AutoCloseable {
    private val assign = settings.assignedProfile?.let { IccTransform(source=profile(it), sourceBuiltin=0, destinationBuiltin=1,
        intent=settings.intent.code) }
    private val output = IccTransform(destination=if(preview) null else cmykProfile ?: settings.outputProfile?.let(::profile),
        destinationBuiltin=if(preview || cmykProfile!=null) 1 else if(settings.outputSpace==RgbOutputSpace.DISPLAY_P3) 2 else 0,
        proof=if(preview && settings.softProof) settings.proofProfile?.let(::profile) else null,
        intent=settings.intent.code, cmyk=cmykProfile!=null, gamutCheck=preview && settings.softProof && settings.gamutWarning)
    private var scratch = FloatArray(0)
    fun convert(linearStraight: FloatArray, rgb: FloatArray? = null, cmyk: ByteArray? = null) {
        var input=linearStraight
        assign?.let { transform ->
            if(scratch.size!=input.size) scratch=FloatArray(input.size)
            for(i in input.indices) scratch[i]=if(i%4==3) input[i] else encode(input[i])
            transform.rgb(scratch,scratch); input=scratch
        }
        if(cmyk!=null) output.cmyk(input,cmyk) else output.rgb(input,requireNotNull(rgb))
    }
    override fun close() { assign?.close(); output.close() }
    companion object {
        fun profile(path:String):ByteArray { val file=File(path); require(file.length() in 128..16*1024*1024); return file.readBytes() }
        private fun encode(value:Float):Float { val sign=if(value<0) -1 else 1; val c=kotlin.math.abs(value); return sign*if(c<=.0031308f) c*12.92f else 1.055f*c.pow(1f/2.4f)-.055f }
    }
}

/** Canonical backdrop coordinates are normalized to the complete page, including framed image paper. */
internal data class IccBackdrop(val pixels:PixelTileSurfaceSnapshot,val left:Float=0f,val top:Float=0f,
    val right:Float=1f,val bottom:Float=1f) {
    fun sample(x:Float,y:Float):LinearRgba {
        if(x<left || y<top || x>=right || y>=bottom || right<=left || bottom<=top) return LinearRgba(0f,0f,0f,0f)
        val sx=((x-left)/(right-left)*pixels.logicalWidth-.5f).coerceIn(0f,(pixels.logicalWidth-1).toFloat())
        val sy=((y-top)/(bottom-top)*pixels.logicalHeight-.5f).coerceIn(0f,(pixels.logicalHeight-1).toFloat())
        val ix=kotlin.math.floor(sx).toInt();val iy=kotlin.math.floor(sy).toInt();val fx=sx-ix;val fy=sy-iy
        val a=pixels.linearPixelAt(ix,iy);val b=pixels.linearPixelAt(minOf(ix+1,pixels.logicalWidth-1),iy)
        val c=pixels.linearPixelAt(ix,minOf(iy+1,pixels.logicalHeight-1));val d=pixels.linearPixelAt(minOf(ix+1,pixels.logicalWidth-1),minOf(iy+1,pixels.logicalHeight-1))
        fun mix(a:Float,b:Float,c:Float,d:Float)=(a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy
        return LinearRgba(mix(a.red,b.red,c.red,d.red),mix(a.green,b.green,c.green,d.green),
            mix(a.blue,b.blue,c.blue,d.blue),mix(a.alpha,b.alpha,c.alpha,d.alpha))
    }
}

/** Cache by immutable tile generation; a proof preview never changes editable pixels or history. */
internal class IccPreviewCache(private val settings: PageColorManagement,
    private val releaseSurface:(String)->Unit = {}) : AutoCloseable {
    private val pipeline=IccColorPipeline(settings,preview=true)
    private var sourceId:String?=null
    private var output:PixelTileSurface?=null
    private val generations=mutableMapOf<PixelTileKey,Long>()
    private var backdropIdentity:IccBackdrop?=null
    fun update(source:PixelTileSurfaceSnapshot,backdrop:IccBackdrop?=null):PixelTileSurfaceSnapshot {
        if(backdropIdentity!=backdrop) { generations.keys.toList().forEach { generations[it]=Long.MIN_VALUE };backdropIdentity=backdrop }
        var target=output
        if(sourceId!=source.surfaceId || target==null || target.logicalWidth!=source.logicalWidth || target.logicalHeight!=source.logicalHeight || target.tileSize!=source.tileSize) {
            target?.surfaceId?.let(releaseSurface)
            target=PixelTileSurface(source.logicalWidth,source.logicalHeight,source.tileSize,pixelFormat=PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
            output=target; sourceId=source.surfaceId; generations.clear()
        }
        val keys=source.tileInfo.associateBy { it.key }
        target.beginWrite("ICC preview").use { write ->
            (generations.keys-keys.keys).forEach { key -> write.editTileF16(key,PixelRect(0,0,source.tileSize,source.tileSize)) { p,_->p.fill(0) }; generations.remove(key) }
            keys.forEach { (key,info) ->
                if(generations[key]!=info.generation) {
                    val input=FloatArray(source.tileSize*source.tileSize*4)
                    val tileWidth=minOf(source.tileSize,source.logicalWidth-key.column*source.tileSize)
                    val tileHeight=minOf(source.tileSize,source.logicalHeight-key.row*source.tileSize)
                    for(y in 0 until tileHeight) for(x in 0 until tileWidth) {
                        val px=key.column*source.tileSize+x;val py=key.row*source.tileSize+y
                        val color=source.linearPixelAt(px,py)
                        val bg=backdrop?.sample((px+.5f)/source.logicalWidth,(py+.5f)/source.logicalHeight)
                        val a=color.alpha+(bg?.alpha ?: 0f)*(1f-color.alpha)
                        val i=(y*source.tileSize+x)*4
                        if(a>0) {
                            input[i]=(color.red+(bg?.red ?: 0f)*(1f-color.alpha))/a
                            input[i+1]=(color.green+(bg?.green ?: 0f)*(1f-color.alpha))/a
                            input[i+2]=(color.blue+(bg?.blue ?: 0f)*(1f-color.alpha))/a
                            input[i+3]=a
                        }
                    }
                    val result=FloatArray(input.size); pipeline.convert(input,result)
                    write.editTileF16(key,PixelRect(0,0,source.tileSize,source.tileSize)) { p,_-> for(i in p.indices) p[i]=PackedRgbaF16.packStraight(LinearRgba(result[i*4],result[i*4+1],result[i*4+2],result[i*4+3])) }
                    generations[key]=info.generation
                }
            }; write.commit().close()
        }; return target.snapshot()
    }
    override fun close() { pipeline.close(); output?.surfaceId?.let(releaseSurface);output=null; backdropIdentity=null; generations.clear() }
}
