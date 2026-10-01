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
        intent=settings.intent.code, cmyk=cmykProfile!=null)
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

/** Cache by immutable tile generation; a proof preview never changes editable pixels or history. */
internal class IccPreviewCache(private val settings: PageColorManagement) : AutoCloseable {
    private val pipeline=IccColorPipeline(settings,preview=true)
    private var sourceId:String?=null
    private var output:PixelTileSurface?=null
    private val generations=mutableMapOf<PixelTileKey,Long>()
    fun update(source:PixelTileSurfaceSnapshot):PixelTileSurfaceSnapshot {
        var target=output
        if(sourceId!=source.surfaceId || target==null || target.logicalWidth!=source.logicalWidth || target.logicalHeight!=source.logicalHeight || target.tileSize!=source.tileSize) {
            target=PixelTileSurface(source.logicalWidth,source.logicalHeight,source.tileSize,pixelFormat=PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
            output=target; sourceId=source.surfaceId; generations.clear()
        }
        val keys=source.tileInfo.associateBy { it.key }
        target.beginWrite("ICC preview").use { write ->
            (generations.keys-keys.keys).forEach { key -> write.editTileF16(key,PixelRect(0,0,source.tileSize,source.tileSize)) { p,_->p.fill(0) }; generations.remove(key) }
            keys.forEach { (key,info) ->
                if(generations[key]!=info.generation) {
                    val input=FloatArray(source.tileSize*source.tileSize*4)
                    for(y in 0 until source.tileSize) for(x in 0 until source.tileSize) {
                        val color=source.linearPixelAt(key.column*source.tileSize+x,key.row*source.tileSize+y); val a=color.alpha
                        val i=(y*source.tileSize+x)*4
                        if(a>0) { input[i]=color.red/a; input[i+1]=color.green/a; input[i+2]=color.blue/a; input[i+3]=a }
                    }
                    val result=FloatArray(input.size); pipeline.convert(input,result)
                    write.editTileF16(key,PixelRect(0,0,source.tileSize,source.tileSize)) { p,_-> for(i in p.indices) p[i]=PackedRgbaF16.packStraight(LinearRgba(result[i*4],result[i*4+1],result[i*4+2],result[i*4+3])) }
                    generations[key]=info.generation
                }
            }; write.commit().close()
        }; return target.snapshot()
    }
    override fun close() { pipeline.close(); output=null; generations.clear() }
}
