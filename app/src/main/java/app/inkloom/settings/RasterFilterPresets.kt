package app.inkloom.settings

import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.engine.v5.tools.ArtisticParameters
import app.inkloom.engine.v5.tools.ArtisticProcess
import app.inkloom.engine.v5.tools.DistortKind
import app.inkloom.engine.v5.tools.FilmLook
import app.inkloom.engine.v5.tools.FilmParameters
import app.inkloom.engine.v5.tools.DropShadowParameters
import app.inkloom.engine.v5.tools.GlitchParameters
import app.inkloom.engine.v5.tools.GlowParameters
import app.inkloom.engine.v5.tools.InflateParameters
import app.inkloom.engine.v5.tools.LayerConversion
import app.inkloom.engine.v5.tools.LayerExpression
import app.inkloom.engine.v5.tools.PencilParameters
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
    val adjustment:LayerAdjustment?=null,
    // CSP Filter > Effect settings, in CSP's units (0..100 unless noted).
    val cellSize:Float=24f,val randomness:Float=70f,val tiling:Boolean=false,val normalStrength:Float=30f,
    val artProcess:Int=0,val lineWidth:Float=2f,val lineSimplicity:Float=40f,val lineDensity:Float=50f,val lineOpacity:Float=100f,
    val lineAntiAliasing:Float=50f,val colorBlending:Float=50f,val colorBlur:Float=20f,val colorCount:Int=8,
    val pencilOutline:Boolean=true,val pencilHatching:Boolean=true,val hatchSize:Float=2f,val hatchRoughness:Float=40f,val grayscale:Boolean=true,
    val filmIntensity:Float=70f,val filmAberration:Float=30f,val filmNoise:Float=30f,
    // Convert layer: expression 0 colour, 1 grey, 2 monochrome; resolution as a fraction; tone pitch in px (0 = no tone).
    val expression:Int=1,val monoThreshold:Float=.5f,val whiteTransparent:Boolean=true,val layerResolution:Float=1f,
    val tonePitch:Float=0f,
    // Match color: the reference layer's statistics are read when the filter runs.
    val matchStrength:Float=1f,val keepLuminance:Boolean=false,
    // Glow, Inflate, Drop shadow (angle and colour shared) and Glitch (seed shared).
    val glowStrength:Float=100f,val glowArea:Float=50f,val glowBlur:Float=30f,
    val inflateShadow:Float=50f,val inflateShine:Float=50f,val inflateHeight:Float=30f,val inflateSmoothness:Float=2f,
    val shadowBlur:Float=8f,val shadowDistance:Float=12f,val shadowOpacity:Float=60f,
    val glitchShift:Float=40f,val glitchBand:Float=24f,val glitchDensity:Float=40f,val glitchSplit:Float=6f) {
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
        attenuation=attenuation.bounded(0f..1f,.5f),octaves=octaves.coerceIn(1,8),
        cellSize=cellSize.bounded(2f..200f,24f),randomness=randomness.bounded(0f..100f,70f),normalStrength=normalStrength.bounded(1f..100f,30f),
        artProcess=artProcess.coerceIn(0,2),lineWidth=lineWidth.bounded(.5f..10f,2f),lineSimplicity=lineSimplicity.bounded(0f..100f,40f),
        lineDensity=lineDensity.bounded(0f..100f,50f),lineOpacity=lineOpacity.bounded(0f..100f,100f),lineAntiAliasing=lineAntiAliasing.bounded(0f..100f,50f),
        colorBlending=colorBlending.bounded(0f..100f,50f),colorBlur=colorBlur.bounded(0f..100f,20f),colorCount=colorCount.coerceIn(2,64),
        hatchSize=hatchSize.bounded(1f..10f,2f),hatchRoughness=hatchRoughness.bounded(0f..100f,40f),
        filmIntensity=filmIntensity.bounded(0f..100f,70f),filmAberration=filmAberration.bounded(0f..100f,30f),filmNoise=filmNoise.bounded(0f..100f,30f),
        expression=expression.coerceIn(0,2),monoThreshold=monoThreshold.bounded(0f..1f,.5f),layerResolution=layerResolution.bounded(.02f..1f,1f),
        tonePitch=tonePitch.takeIf{it.isFinite()&&it>=2f}?.coerceAtMost(200f)?:0f,matchStrength=matchStrength.bounded(0f..1f,1f),
        glowStrength=glowStrength.bounded(0f..400f,100f),glowArea=glowArea.bounded(0f..100f,50f),glowBlur=glowBlur.bounded(1f..100f,30f),
        inflateShadow=inflateShadow.bounded(0f..100f,50f),inflateShine=inflateShine.bounded(0f..100f,50f),inflateHeight=inflateHeight.bounded(0f..100f,30f),
        inflateSmoothness=inflateSmoothness.bounded(0f..10f,2f),shadowBlur=shadowBlur.bounded(0f..100f,8f),shadowDistance=shadowDistance.bounded(0f..500f,12f),
        shadowOpacity=shadowOpacity.bounded(0f..100f,60f),glitchShift=glitchShift.bounded(0f..400f,40f),glitchBand=glitchBand.bounded(2f..400f,24f),
        glitchDensity=glitchDensity.bounded(0f..100f,40f),glitchSplit=glitchSplit.bounded(0f..100f,6f))
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
        in FIRST_TONAL_KIND..LAST_TONAL_KIND->RasterImageFilter.Effect.Tonal((p.adjustment ?: LayerAdjustment()).copy(kind=TONAL_KINDS[p.kind-FIRST_TONAL_KIND]))
        FIRST_EFFECT_KIND->RasterImageFilter.Effect.RemoveJpegNoise
        FIRST_EFFECT_KIND+1->RasterImageFilter.Effect.Crystallize(p.cellSize,p.randomness,p.tiling,p.seed)
        FIRST_EFFECT_KIND+2->RasterImageFilter.Effect.NormalMap(p.normalStrength,yUp=p.mode==0)
        FIRST_EFFECT_KIND+3->RasterImageFilter.Effect.Artistic(ArtisticParameters(ArtisticProcess.entries[p.artProcess],p.lineWidth,p.lineSimplicity,
            p.lineDensity,p.lineOpacity,p.lineAntiAliasing,p.colorBlending,p.colorBlur,p.colorCount))
        FIRST_EFFECT_KIND+4->RasterImageFilter.Effect.PencilDrawing(PencilParameters(p.pencilOutline,p.pencilHatching,p.hatchSize,p.hatchRoughness,p.angle,p.grayscale,p.seed))
        FIRST_EFFECT_KIND+5->RasterImageFilter.Effect.Film(FilmParameters(FilmLook.entries[p.mode.coerceIn(0,2)],p.filmIntensity,p.filmAberration,p.filmNoise,p.seed))
        CONVERT_LAYER_KIND->RasterImageFilter.Effect.ConvertLayer(LayerConversion(LayerExpression.entries[p.expression],p.monoThreshold,
            p.whiteTransparent,p.layerResolution,p.tonePitch.takeIf{it>=2f},p.angle))
        MATCH_COLOR_KIND->RasterImageFilter.Effect.MatchColor(null,p.matchStrength,p.keepLuminance)
        GLOW_KIND->RasterImageFilter.Effect.Glow(GlowParameters(p.glowStrength,p.glowArea,p.glowBlur))
        INFLATE_KIND->RasterImageFilter.Effect.Inflate(InflateParameters(p.inflateShadow,p.inflateShine,p.inflateHeight,p.inflateSmoothness,p.angle))
        DROP_SHADOW_KIND->RasterImageFilter.Effect.DropShadow(DropShadowParameters(p.shadowBlur,p.angle,p.shadowDistance,p.shadowOpacity,p.colorArgb))
        GLITCH_KIND->RasterImageFilter.Effect.Glitch(GlitchParameters(p.glitchShift,p.glitchBand,p.glitchDensity,p.glitchSplit,p.seed))
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
        val LAST_TONAL_KIND=FIRST_TONAL_KIND+TONAL_KINDS.lastIndex
        /** Remove JPEG noise, Crystallize, Normal map, Artistic, Pencil drawing and Film. */
        val FIRST_EFFECT_KIND=LAST_TONAL_KIND+1
        /** CSP Layer > Convert layer, opened from the layer menu. */
        val CONVERT_LAYER_KIND=FIRST_EFFECT_KIND+6
        /** Match color: the dialog supplies the reference layer's statistics. */
        val MATCH_COLOR_KIND=CONVERT_LAYER_KIND+1
        /** CSP 5.1's Glow, Inflate and Drop shadow, then Glitch; listed with the Effect filters. */
        val GLOW_KIND=MATCH_COLOR_KIND+1
        val INFLATE_KIND=GLOW_KIND+1
        val DROP_SHADOW_KIND=INFLATE_KIND+1
        val GLITCH_KIND=DROP_SHADOW_KIND+1
        val LAST_KIND=GLITCH_KIND
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
