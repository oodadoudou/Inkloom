package app.inkloom.settings

import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.engine.v5.tools.DistortKind
import app.inkloom.engine.v5.tools.DistortParameters
import app.inkloom.engine.v5.tools.FixedBlurKind
import app.inkloom.engine.v5.tools.RasterImageFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class RasterFilterPreset(val id:String=UUID.randomUUID().toString(),val name:String="",val favorite:Boolean=false,
    val kind:Int=0,val strength:Float=2f,val amount:Float=1.2f,val wavelength:Float=64f,val grow:Boolean=true,
    val angle:Float=0f,val centerX:Float=.5f,val centerY:Float=.5f,val zoom:Boolean=false,val seed:Int=1,val monochrome:Boolean=true,val colorArgb:Int=0xFF111111.toInt(),
    // Distort and Perlin noise settings, in CSP's units.
    val distortion:Float=50f,val radius:Float=0f,val shape:Float=0f,val tension:Float=0f,val count:Int=3,
    val wavelengthMin:Float=40f,val wavelengthMax:Float=120f,val amplitudeMin:Float=5f,val amplitudeMax:Float=20f,
    val horizontalRatio:Float=100f,val verticalRatio:Float=100f,val mode:Int=0,val wrap:Boolean=false,val scale:Float=100f,
    val noiseScale:Float=64f,val noiseAmplitude:Float=.5f,val attenuation:Float=.5f,val octaves:Int=4,
    /** Tonal correction settings for kinds from [FIRST_TONAL_KIND]. */
    val adjustment:LayerAdjustment?=null) {
    fun normalized():RasterFilterPreset {
        fun Float.bounded(range:ClosedFloatingPointRange<Float>,fallback:Float)=takeIf{it.isFinite()}?.coerceIn(range)?:fallback
        val lengthMin=wavelengthMin.bounded(2f..2000f,40f);val heightMin=amplitudeMin.bounded(0f..1000f,5f)
        return copy(name=name.trim().take(48),kind=kind.coerceIn(0,LAST_KIND),strength=strength.bounded(1f..20f,2f),
        amount=amount.bounded(0f..4f,1.2f),wavelength=wavelength.bounded(16f..512f,64f),
        angle=angle.bounded(-180f..180f,0f),centerX=centerX.bounded(0f..1f,.5f),centerY=centerY.bounded(0f..1f,.5f),
        distortion=distortion.bounded(-100f..100f,50f),radius=radius.bounded(0f..1f,0f),shape=shape.bounded(-100f..100f,0f),
        tension=tension.bounded(0f..100f,0f),count=count.coerceIn(1,20),wavelengthMin=lengthMin,wavelengthMax=wavelengthMax.bounded(lengthMin..2000f,120f.coerceAtLeast(lengthMin)),
        amplitudeMin=heightMin,amplitudeMax=amplitudeMax.bounded(heightMin..1000f,20f.coerceAtLeast(heightMin)),
        horizontalRatio=horizontalRatio.bounded(0f..100f,100f),verticalRatio=verticalRatio.bounded(0f..100f,100f),mode=mode.coerceIn(0,2),
        scale=scale.bounded(10f..400f,100f),noiseScale=noiseScale.bounded(2f..2000f,64f),noiseAmplitude=noiseAmplitude.bounded(0f..1f,.5f),
        attenuation=attenuation.bounded(0f..1f,.5f),octaves=octaves.coerceIn(1,8))
    }
    internal fun distortParameters()=DistortParameters(distortion,centerX,centerY,radius,shape,angle,tension,count,
        wavelengthMin,wavelengthMax,amplitudeMin,amplitudeMax,horizontalRatio,verticalRatio,mode,wrap,scale,seed)
    internal fun effect():RasterImageFilter.Effect {val p=normalized();return when(p.kind){
        0->RasterImageFilter.Effect.GaussianBlur(p.strength);1->RasterImageFilter.Effect.UnsharpMask(p.strength,p.amount)
        2->RasterImageFilter.Effect.Denoise(p.strength.coerceAtMost(3f));3->RasterImageFilter.Effect.CleanLineArt(p.strength)
        4->RasterImageFilter.Effect.AdjustLineWidth(p.strength.coerceAtMost(3f),p.grow);5->RasterImageFilter.Effect.Wave(p.strength,p.wavelength)
        6->RasterImageFilter.Effect.MotionBlur(p.strength,p.angle);7->RasterImageFilter.Effect.RadialBlur(p.strength,p.centerX,p.centerY,p.zoom)
        8->RasterImageFilter.Effect.LensBlur(p.strength);9->RasterImageFilter.Effect.Mosaic(p.strength)
        10->RasterImageFilter.Effect.Noise(p.strength,p.seed,p.monochrome);11->RasterImageFilter.Effect.ChromaticAberration(p.strength,p.angle)
        12->RasterImageFilter.Effect.ConvertToDrawingColor(p.colorArgb);13->RasterImageFilter.Effect.BrightnessToOpacity
        14->RasterImageFilter.Effect.Sharpen(strong=false);15->RasterImageFilter.Effect.Sharpen(strong=true)
        16->RasterImageFilter.Effect.FixedBlur(FixedBlurKind.BLUR);17->RasterImageFilter.Effect.FixedBlur(FixedBlurKind.STRONG_BLUR)
        18->RasterImageFilter.Effect.FixedBlur(FixedBlurKind.SMOOTH)
        19->RasterImageFilter.Effect.PerlinNoise(p.noiseScale,p.noiseAmplitude,p.attenuation,p.octaves,p.seed)
        in FIRST_TONAL_KIND..LAST_KIND->RasterImageFilter.Effect.Tonal((p.adjustment ?: LayerAdjustment()).copy(kind=TONAL_KINDS[p.kind-FIRST_TONAL_KIND]))
        else->RasterImageFilter.Effect.Distort(DISTORT_KINDS[p.kind-FIRST_DISTORT_KIND],p.distortParameters())
    }}
    companion object {
        const val FIRST_DISTORT_KIND=20
        /** Kinds from [FIRST_DISTORT_KIND] in this order; saved presets store the index. */
        val DISTORT_KINDS=listOf(DistortKind.PINCH,DistortKind.CURVED_SURFACE,DistortKind.WAVE,DistortKind.TWIRL,DistortKind.PANORAMA,
            DistortKind.GEOMETRIC,DistortKind.POLAR,DistortKind.ZIGZAG,DistortKind.FISHEYE)
        val FIRST_TONAL_KIND=FIRST_DISTORT_KIND+DISTORT_KINDS.size
        /** CSP Edit > Tonal correction, in CSP's menu order. */
        val TONAL_KINDS=listOf(LayerAdjustmentKind.BRIGHTNESS_CONTRAST,LayerAdjustmentKind.HUE_SATURATION,LayerAdjustmentKind.LEVELS,
            LayerAdjustmentKind.CURVES,LayerAdjustmentKind.COLOR_BALANCE,LayerAdjustmentKind.INVERT,LayerAdjustmentKind.POSTERIZE,
            LayerAdjustmentKind.THRESHOLD,LayerAdjustmentKind.GRADIENT_MAP)
        val LAST_KIND=FIRST_TONAL_KIND+TONAL_KINDS.lastIndex
    }
}
object RasterFilterPresetsCodec {
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    fun encode(values:List<RasterFilterPreset>)=json.encodeToString(ListSerializer(RasterFilterPreset.serializer()),values.take(100).map{it.normalized()})
    fun decode(text:String?):List<RasterFilterPreset> = if(text.isNullOrBlank())emptyList() else runCatching{
        require(text.length<=256*1024)
        json.decodeFromString(ListSerializer(RasterFilterPreset.serializer()),text).map{it.normalized()}.filter{it.name.isNotBlank()}.distinctBy{it.id}.take(100)
    }.getOrDefault(emptyList())
}
