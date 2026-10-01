package app.inkloom.settings

import app.inkloom.engine.v5.tools.RasterImageFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class RasterFilterPreset(val id:String=UUID.randomUUID().toString(),val name:String="",val favorite:Boolean=false,
    val kind:Int=0,val strength:Float=2f,val amount:Float=1.2f,val wavelength:Float=64f,val grow:Boolean=true,
    val angle:Float=0f,val centerX:Float=.5f,val centerY:Float=.5f,val zoom:Boolean=false,val seed:Int=1,val monochrome:Boolean=true) {
    fun normalized()=copy(name=name.trim().take(48),kind=kind.coerceIn(0,11),strength=strength.takeIf{it.isFinite()}?.coerceIn(1f,20f)?:2f,
        amount=amount.takeIf{it.isFinite()}?.coerceIn(0f,4f)?:1.2f,wavelength=wavelength.takeIf{it.isFinite()}?.coerceIn(16f,512f)?:64f,
        angle=angle.takeIf{it.isFinite()}?.coerceIn(-180f,180f)?:0f,centerX=centerX.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f,centerY=centerY.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:.5f)
    internal fun effect():RasterImageFilter.Effect {val p=normalized();return when(p.kind){
        0->RasterImageFilter.Effect.GaussianBlur(p.strength);1->RasterImageFilter.Effect.UnsharpMask(p.strength,p.amount)
        2->RasterImageFilter.Effect.Denoise(p.strength.coerceAtMost(3f));3->RasterImageFilter.Effect.CleanLineArt(p.strength)
        4->RasterImageFilter.Effect.AdjustLineWidth(p.strength.coerceAtMost(3f),p.grow);5->RasterImageFilter.Effect.Wave(p.strength,p.wavelength)
        6->RasterImageFilter.Effect.MotionBlur(p.strength,p.angle);7->RasterImageFilter.Effect.RadialBlur(p.strength,p.centerX,p.centerY,p.zoom)
        8->RasterImageFilter.Effect.LensBlur(p.strength);9->RasterImageFilter.Effect.Mosaic(p.strength)
        10->RasterImageFilter.Effect.Noise(p.strength,p.seed,p.monochrome);else->RasterImageFilter.Effect.ChromaticAberration(p.strength,p.angle)
    }}
}
object RasterFilterPresetsCodec {
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    fun encode(values:List<RasterFilterPreset>)=json.encodeToString(ListSerializer(RasterFilterPreset.serializer()),values.take(100).map{it.normalized()})
    fun decode(text:String?):List<RasterFilterPreset> = if(text.isNullOrBlank())emptyList() else runCatching{
        require(text.length<=256*1024)
        json.decodeFromString(ListSerializer(RasterFilterPreset.serializer()),text).map{it.normalized()}.filter{it.name.isNotBlank()}.distinctBy{it.id}.take(100)
    }.getOrDefault(emptyList())
}
