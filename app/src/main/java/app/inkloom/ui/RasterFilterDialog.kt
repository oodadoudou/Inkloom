package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.inkloom.engine.v5.tools.RasterImageFilter
import app.inkloom.settings.*

/** CSP-style filter window docked at the side, so the live preview on the canvas stays visible. */
@Composable
internal fun RasterFilterDialog(drawingColor:Int,presets:List<RasterFilterPreset>,onPresets:(List<RasterFilterPreset>)->Unit,
    compositePreview:Bitmap?,copyBusy:Boolean,onPreview:(RasterImageFilter.Effect,Boolean)->Unit,
    onApply:(RasterImageFilter.Effect,Boolean)->Unit,onCancel:()->Unit,initialKind:Int=0,pageDpi:Float=300f,
    /** Layers that can serve as a colour reference (ID to name), and how to read one's statistics. */
    referenceLayers:List<Pair<String,String>> = emptyList(),
    referenceStatistics:suspend (String)->app.inkloom.engine.v5.tools.ColorStatistics? = { null }) {
    val language=LocalUiLanguage.current
    fun tr(en:String,zh:String)=language.text(en,zh)
    var state by remember{mutableStateOf(RasterFilterPreset(colorArgb=drawingColor,kind=initialKind))}
    var channel by remember{mutableIntStateOf(0)}
    var composite by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("")}
    var presetMenu by remember{mutableStateOf(false)}
    var kinds by remember{mutableStateOf(false)}
    var referenceId by remember{mutableStateOf(referenceLayers.firstOrNull()?.first)}
    var referenceMenu by remember{mutableStateOf(false)}
    val reference by produceState<app.inkloom.engine.v5.tools.ColorStatistics?>(null,referenceId){ value=referenceId?.let{referenceStatistics(it)} }
    val effect=state.effect().let{ if(it is RasterImageFilter.Effect.MatchColor) it.copy(reference=reference) else it }
    LaunchedEffect(effect,composite){onPreview(effect,composite)}
    val names=rasterFilterNames(::tr)
    var colorPickerOpen by remember { mutableStateOf(false) }
    if (colorPickerOpen) StandardColorPickerDialog(state.colorArgb or 0xFF000000.toInt(), language,
        onConfirm = { state=state.copy(colorArgb=it or 0xFF000000.toInt());colorPickerOpen=false },
        onDismiss = { colorPickerOpen=false })
    StudioDialog(properties = DialogProperties(decorFitsSystemWindows = false), placement = StudioDialogPlacement.SIDE,
        onDismissRequest={if(!copyBusy)onCancel()},title={Text(tr("Image filter","图像滤镜"))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StudioDropdownField(names[state.kind],kinds,{kinds=it},label=tr("Filter","滤镜")){
                RasterFilterKindMenuItems(::tr){i->
                    state=state.copy(kind=i,strength=when(i){2,3,4->1f;5->8f;else->2f},distortion=if(i==28)kotlin.math.abs(state.distortion) else state.distortion,
                        angle=when(i){RasterFilterPreset.INFLATE_KIND->135f;RasterFilterPreset.DROP_SHADOW_KIND->-45f;else->state.angle});kinds=false}
            }
            StudioDropdownField(presets.firstOrNull{it.id==state.id}?.let{(if(it.favorite) "★ " else "")+it.name} ?: tr("None","无"),presetMenu,{presetMenu=it},
                label=tr("Preset","预设")){
                presets.sortedByDescending{it.favorite}.forEach{p->StudioMenuItem(text={Text((if(p.favorite) "★ " else "")+p.name)},
                    leadingIcon={RasterFilterThumbnail(p)},onClick={state=p;presetMenu=false})}
                if(presets.isEmpty())StudioMenuItem(text={Text(tr("No saved presets","暂无预设"))},onClick={},enabled=false)
            }
            HorizontalDivider(Modifier.padding(vertical=6.dp),color=MaterialTheme.colorScheme.outline)
            RasterFilterBasicControls(state,{state=it},::tr) { colorPickerOpen=true }
            if (state.kind == RasterFilterPreset.MATCH_COLOR_KIND) {
                StudioDropdownField(referenceLayers.firstOrNull{it.first==referenceId}?.second ?: tr("No other layer","没有其他图层"),referenceMenu,{referenceMenu=it},label=tr("Reference","参考图层")){
                    referenceLayers.forEach{(id,name)->StudioMenuItem(text={Text(name)},onClick={referenceId=id;referenceMenu=false})}
                }
                StudioSliderRow(tr("Strength","强度"),state.matchStrength*100f,0f..100f,{state=state.copy(matchStrength=it/100f)},unit="%")
                StudioCheckRow(tr("Keep luminance","保持明度"),state.keepLuminance,{state=state.copy(keepLuminance=it)})
            } else if (state.kind >= RasterFilterPreset.FIRST_EFFECT_KIND) RasterFilterEffectControls(state,{state=it},::tr,pageDpi) { colorPickerOpen=true }
            else if (state.kind >= RasterFilterPreset.FIRST_TONAL_KIND) {
                val kind=RasterFilterPreset.TONAL_KINDS[state.kind-RasterFilterPreset.FIRST_TONAL_KIND]
                AdjustmentParameters((state.adjustment ?: app.inkloom.document.LayerAdjustment()).copy(kind=kind),channel,{channel=it},{state=state.copy(adjustment=it)},language)
            } else if (state.kind >= 16) RasterFilterExtraControls(state,{state=it},::tr)
            HorizontalDivider(Modifier.padding(vertical=6.dp),color=MaterialTheme.colorScheme.outline)
            StudioCheckRow(tr("Process visible copy","处理可见画面副本"),composite,{composite=it},enabled=!copyBusy)
            if(composite){
                compositePreview?.takeUnless{it.isRecycled}?.let{Image(it.asImageBitmap(),tr("Composite preview","合成预览"),Modifier.fillMaxWidth().heightIn(max=160.dp))}
                if(copyBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                StudioTextField(name,{name=it.take(48)},tr("Preset name","预设名称"),Modifier.weight(1f))
                StudioButton(tr("Save","保存"),{val p=app.inkloom.settings.RasterFilterAlgorithms.stamped(state.copy(id=java.util.UUID.randomUUID().toString(),name=name.trim()));onPresets((presets+p).takeLast(100));state=p},enabled=name.isNotBlank())
            }
            presets.firstOrNull{it.id==state.id && it.kind==state.kind}?.takeIf(app.inkloom.settings.RasterFilterAlgorithms::outdated)?.let {
                StudioNote(tr("Saved with an older version of this filter.","此预设由旧版滤镜保存。"),Modifier.padding(top=4.dp))
            }
            presets.firstOrNull{it.id==state.id}?.let{saved->Row(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                StudioButton(if(saved.favorite) tr("★ Favorite","★ 已收藏") else tr("☆ Favorite","☆ 收藏"),{onPresets(presets.map{if(it.id==saved.id)it.copy(favorite=!it.favorite)else it})})
                StudioButton(tr("Delete preset","删除预设"),{onPresets(presets.filterNot{it.id==saved.id})})
            }}
        }
    },confirmButton={TextButton({onApply(effect,composite)},enabled=!copyBusy){Text(tr("Apply","应用"))}},dismissButton={TextButton(onCancel,enabled=!copyBusy){Text(tr("Cancel","取消"))}})
}

/** Settings for the original sixteen filters (kinds 0–15). */
@Composable
private fun RasterFilterBasicControls(state:RasterFilterPreset,onState:(RasterFilterPreset)->Unit,tr:(String,String)->String,onPickColor:()->Unit) {
    val note=MaterialTheme.typography.bodySmall
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    if (state.kind < 12) StudioSliderRow(tr("Strength","强度"),state.strength,1f..if(state.kind in listOf(2,4))3f else 20f,{onState(state.copy(strength=it))})
    if (state.kind == 12) StudioButton(tr("Drawing color…","绘画色…"),onPickColor) {
        Box(Modifier.size(14.dp).background(Color(state.colorArgb or 0xFF000000.toInt())))
    }
    if(state.kind==1)StudioSliderRow(tr("Amount","锐化量"),state.amount,0f..4f,{onState(state.copy(amount=it))},decimals=2)
    if(state.kind==4)StudioSegmented(listOf(tr("Thicken","加粗"),tr("Thin","变细")),if(state.grow)0 else 1,{onState(state.copy(grow=it==0))},label=tr("Direction","方向"))
    if(state.kind==5)StudioSliderRow(tr("Wavelength","波长"),state.wavelength,16f..512f,{onState(state.copy(wavelength=it))},unit="px")
    if(state.kind==6 || state.kind==11)StudioSliderRow(tr("Angle","角度"),state.angle,-180f..180f,{onState(state.copy(angle=it))},unit="°")
    if(state.kind==7){
        StudioSegmented(listOf(tr("Spin","旋转"),tr("Zoom","缩放")),if(state.zoom)1 else 0,{onState(state.copy(zoom=it==1))},label=tr("Type","类型"))
        StudioSliderRow(tr("Center X","中心 X"),state.centerX*100f,0f..100f,{onState(state.copy(centerX=it/100f))},unit="%")
        StudioSliderRow(tr("Center Y","中心 Y"),state.centerY*100f,0f..100f,{onState(state.copy(centerY=it/100f))},unit="%")
    }
    if(state.kind==10){
        StudioCheckRow(tr("Monochrome","单色"),state.monochrome,{onState(state.copy(monochrome=it))})
        StudioButton(tr("New noise","重新生成噪点"),{onState(state.copy(seed=state.seed+1))})
    }
}
