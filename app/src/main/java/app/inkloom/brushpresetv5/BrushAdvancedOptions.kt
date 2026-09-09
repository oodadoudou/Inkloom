package app.inkloom.brushpresetv5

internal object BrushAdvancedOptions {
    val chinese=mutableMapOf<String,String>()
    private fun stamp(id:String,en:String,zh:String,range:ClosedFloatingPointRange<Float>,get:(StampSettings)->Float,set:(StampSettings,Float)->StampSettings):BrushOptionDescriptor {
        chinese[id]=zh
        return BrushOptionDescriptor(id,en,BrushOptionGroup.SPACING,range,range.start,null,{get(it.stamp ?: StampSettings())},{p,v->p.copy(stamp=set(p.stamp ?: StampSettings(),v))})
    }
    val all=listOf(
        stamp("stampFollowDirectionEnabled","Follow stroke direction","跟随笔画方向",0f..1f,{if(it.followStrokeDirection)1f else 0f},{p,v->p.copy(followStrokeDirection=v>=.5f)}),
        stamp("stampFollowStylusEnabled","Follow pen orientation","跟随笔倾方向",0f..1f,{if(it.followStylusOrientation)1f else 0f},{p,v->p.copy(followStylusOrientation=v>=.5f)}),
        stamp("stampRotationJitter","Rotation jitter","旋转抖动",0f..180f,{it.randomRotationDegrees},{p,v->p.copy(randomRotationDegrees=v)}),
        stamp("stampCount","Stamp count","笔尖数量",1f..32f,{it.particlesPerPlot.toFloat()},{p,v->p.copy(particlesPerPlot=v.toInt())}),
        stamp("stampCountJitter","Count jitter","数量抖动",0f..1f,{it.particlesPerPlotJitter},{p,v->p.copy(particlesPerPlotJitter=v)}),
        stamp("stampLateralJitter","Lateral jitter","横向抖动",0f..1f,{it.lateralPathJitter},{p,v->p.copy(lateralPathJitter=v)}),
        stamp("stampLinearJitter","Linear jitter","纵向抖动",0f..1f,{it.longitudinalPathJitter},{p,v->p.copy(longitudinalPathJitter=v)}),
        stamp("stampHorizontalJitter","Horizontal roundness jitter","水平圆度抖动",0f..1f,{it.horizontalRoundnessJitter},{p,v->p.copy(horizontalRoundnessJitter=v)}),
        stamp("stampVerticalJitter","Vertical roundness jitter","垂直圆度抖动",0f..1f,{it.verticalRoundnessJitter},{p,v->p.copy(verticalRoundnessJitter=v)}),
        stamp("stampDirectionRotation","Direction rotation","方向旋转",-1f..1f,{it.directionRotationFactor},{p,v->p.copy(directionRotationFactor=v)}),
        stamp("stampStrokeRotation","Stroke rotation jitter","每笔旋转抖动",0f..180f,{it.strokeRotationJitterDegrees},{p,v->p.copy(strokeRotationJitterDegrees=v)}),
        stamp("stampTransfer","Rendering mode","渲染模式",0f..5f,{it.transferMode.ordinal.toFloat()},{p,v->p.copy(transferMode=DabTransferMode.entries[v.toInt()])}),
    )
    fun forPreset(p:BrushPresetV5):List<BrushOptionDescriptor> = when(CompiledBrushProgramV5.compile(p).backend) {
        RenderBackend.ASSET_STAMP,RenderBackend.PROCEDURAL_STAMP -> all
        RenderBackend.WET_PAINT -> all.filterNot { it.id in setOf("stampLateralJitter","stampLinearJitter") }
        else -> emptyList()
    }
}
