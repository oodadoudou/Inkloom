package app.inkloom.brushpresetv5

internal object DualBrushEditorOptions {
    private fun option(id:String,label:String,group:BrushOptionGroup,range:ClosedFloatingPointRange<Float>,
        get:(DualBrushSettings)->Float,set:(DualBrushSettings,Float)->DualBrushSettings) =
        BrushOptionDescriptor(id,label,group,range,range.start,null,
            { get(requireNotNull(it.dual)) }, { p,v -> p.copy(dual=set(requireNotNull(p.dual),v)) })
    val shape=listOf(
        option("dualSize","Secondary size",BrushOptionGroup.DUAL_SHAPE,.01f..8f,{it.sizeScale},{p,v->p.copy(sizeScale=v)}),
        option("dualHardness","Secondary hardness",BrushOptionGroup.DUAL_SHAPE,.01f..1f,{it.hardness},{p,v->p.copy(hardness=v)}),
        option("dualAspect","Secondary aspect",BrushOptionGroup.DUAL_SHAPE,.01f..1f,{it.aspect},{p,v->p.copy(aspect=v)}),
        option("dualRotation","Secondary rotation",BrushOptionGroup.DUAL_SHAPE,-180f..180f,{it.rotationDegrees},{p,v->p.copy(rotationDegrees=v)}),
        option("dualRandomRotation","Secondary rotation jitter",BrushOptionGroup.DUAL_SHAPE,0f..180f,{it.randomRotationDegrees},{p,v->p.copy(randomRotationDegrees=v)}),
        option("dualFollowDirectionEnabled","Follow stroke direction",BrushOptionGroup.DUAL_SHAPE,0f..1f,{if(it.followStrokeDirection)1f else 0f},{p,v->p.copy(followStrokeDirection=v>=.5f)}),
        option("dualFollowStylusEnabled","Follow pen orientation",BrushOptionGroup.DUAL_SHAPE,0f..1f,{if(it.followStylusOrientation)1f else 0f},{p,v->p.copy(followStylusOrientation=v>=.5f)}),
    )
    val stroke=listOf(
        option("dualSpacing","Secondary spacing",BrushOptionGroup.DUAL_STROKE,.005f..4f,{it.spacing.value},{p,v->p.copy(spacing=p.spacing.copy(value=v))}),
        option("dualFlow","Secondary flow",BrushOptionGroup.DUAL_STROKE,0f..1f,{it.flow},{p,v->p.copy(flow=v)}),
        option("dualComposite","Brush blend mode",BrushOptionGroup.DUAL_STROKE,0f..DualCompositeMode.entries.lastIndex.toFloat(),{it.compositeMode.ordinal.toFloat()},{p,v->p.copy(compositeMode=DualCompositeMode.entries[v.toInt()])}),
    )
    val grain=BrushMaterialOptions.grain.map { option ->
        option.copy(id="dual"+option.id.replaceFirstChar(Char::uppercaseChar),label="Secondary "+option.label,group=BrushOptionGroup.DUAL_GRAIN,
            get={ p -> option.get(p.copy(textureGrain=requireNotNull(p.dual?.textureGrain))) },
            set={ p,v -> p.copy(dual=requireNotNull(p.dual).copy(textureGrain=option.set(p.copy(textureGrain=requireNotNull(p.dual?.textureGrain)),v).textureGrain)) })
    }
    val all=shape+stroke+grain
    fun forPreset(preset:BrushPresetV5)=if(preset.dual==null) emptyList() else shape+stroke+if(preset.dual.textureGrain!=null)grain else emptyList()
}
