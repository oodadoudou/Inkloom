package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.inkloom.engine.v5.tools.RasterImageFilter
import app.inkloom.settings.*
import java.util.Locale

@Composable
internal fun RasterFilterDialog(drawingColor:Int,presets:List<RasterFilterPreset>,onPresets:(List<RasterFilterPreset>)->Unit,
    compositePreview:Bitmap?,copyBusy:Boolean,onPreview:(RasterImageFilter.Effect,Boolean)->Unit,
    onApply:(RasterImageFilter.Effect,Boolean)->Unit,onCancel:()->Unit) {
    val language=LocalUiLanguage.current
    fun tr(en:String,zh:String)=language.text(en,zh)
    var state by remember{mutableStateOf(RasterFilterPreset(colorArgb=drawingColor))}
    var composite by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("")}
    var presetMenu by remember{mutableStateOf(false)}
    val effect=state.effect()
    LaunchedEffect(effect,composite){onPreview(effect,composite)}
    val names=listOf(tr("Gaussian blur","高斯模糊"),tr("Unsharp mask","USM 锐化"),tr("Denoise","降噪"),tr("Remove stray pixels","清理杂点"),
        tr("Correct line width","修正线宽"),tr("Wave distortion","波形变形"),tr("Motion blur","动感模糊"),tr("Radial blur","径向模糊"),
        tr("Lens blur","镜头模糊"),tr("Mosaic","马赛克"),tr("Noise","噪点"),tr("Chromatic aberration","色差"),
        tr("Convert to drawing color","换为绘画色"),tr("Brightness to opacity","亮度转透明度"))
    var colorPickerOpen by remember { mutableStateOf(false) }
    if (colorPickerOpen) StandardColorPickerDialog(state.colorArgb or 0xFF000000.toInt(), language,
        onConfirm = { state=state.copy(colorArgb=it or 0xFF000000.toInt());colorPickerOpen=false },
        onDismiss = { colorPickerOpen=false })
    AlertDialog(onDismissRequest={if(!copyBusy)onCancel()},title={Text(tr("Image filter","图像滤镜"))},text={
        Column(Modifier.widthIn(min=270.dp,max=420.dp).heightIn(max=520.dp).verticalScroll(rememberScrollState())) {
            Box {
                TextButton({presetMenu=true}){Text(tr("Presets & favorites","预设与收藏"))}
                DropdownMenu(presetMenu,{presetMenu=false}){
                    presets.sortedByDescending{it.favorite}.forEach{p->DropdownMenuItem(text={Text((if(p.favorite) "★ " else "")+p.name)},onClick={state=p;presetMenu=false})}
                    if(presets.isEmpty())DropdownMenuItem(text={Text(tr("No saved presets","暂无预设"))},onClick={},enabled=false)
                }
            }
            Box {
                var kinds by remember{mutableStateOf(false)}
                TextButton({kinds=true}){Text(names[state.kind])}
                DropdownMenu(kinds,{kinds=false}){names.forEachIndexed{i,label->DropdownMenuItem(text={Text(label)},onClick={state=state.copy(kind=i,strength=when(i){2,3,4->1f;5->8f;else->2f});kinds=false})}}
            }
            if (state.kind < 12) {
            Text("${tr("Strength","强度")}  ${String.format(Locale.ROOT,"%.1f",state.strength)}")
            BrushParameterSlider(state.strength,{state=state.copy(strength=it)},valueRange=1f..if(state.kind in listOf(2,4))3f else 20f)
            }
            if (state.kind == 12) TextButton({colorPickerOpen=true}) {
                Box(Modifier.size(18.dp).background(Color(state.colorArgb or 0xFF000000.toInt())))
                Spacer(Modifier.width(8.dp));Text(tr("Drawing color…","绘画色…"))
            }
            if (state.kind == 13) Text(tr("White becomes transparent; darker values become black ink. Source opacity is retained.","白色转为透明；深色转为黑色笔迹，原有透明度继续参与。"),style=MaterialTheme.typography.bodySmall)
            if(state.kind==1){Text(tr("Amount","锐化量"));BrushParameterSlider(state.amount,{state=state.copy(amount=it)},valueRange=0f..4f)}
            if(state.kind==4)Row{FilterChip(state.grow,{state=state.copy(grow=true)},label={Text(tr("Thicken","加粗"))});FilterChip(!state.grow,{state=state.copy(grow=false)},label={Text(tr("Thin","变细"))})}
            if(state.kind==5){Text(tr("Wavelength","波长"));BrushParameterSlider(state.wavelength,{state=state.copy(wavelength=it)},valueRange=16f..512f)}
            if(state.kind==6 || state.kind==11){Text("${tr("Angle","角度")} ${state.angle.toInt()}°");BrushParameterSlider(state.angle,{state=state.copy(angle=it)},valueRange=-180f..180f)}
            if(state.kind==7){
                Row{FilterChip(!state.zoom,{state=state.copy(zoom=false)},label={Text(tr("Spin","旋转"))});FilterChip(state.zoom,{state=state.copy(zoom=true)},label={Text(tr("Zoom","缩放"))})}
                Text("X ${(state.centerX*100).toInt()}%");BrushParameterSlider(state.centerX,{state=state.copy(centerX=it)},valueRange=0f..1f)
                Text("Y ${(state.centerY*100).toInt()}%");BrushParameterSlider(state.centerY,{state=state.copy(centerY=it)},valueRange=0f..1f)
            }
            if(state.kind==10){Row{Checkbox(state.monochrome,{state=state.copy(monochrome=it)});Text(tr("Monochrome","单色"))};TextButton({state=state.copy(seed=state.seed+1)}){Text(tr("New noise","重新生成噪点"))}}
            Row{Checkbox(composite,{composite=it},enabled=!copyBusy);Text(tr("Process visible copy","处理可见画面副本"))}
            if(composite){
                compositePreview?.takeUnless{it.isRecycled}?.let{Image(it.asImageBitmap(),tr("Composite preview","合成预览"),Modifier.fillMaxWidth().heightIn(max=180.dp))}
                if(copyBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(tr("Preview is scaled. Apply creates a pixel layer at authored resolution; source layers stay editable.","预览按比例缩小；应用后按原尺寸创建像素图层，原图层保留。"),style=MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                OutlinedTextField(name,{name=it.take(48)},Modifier.weight(1f),singleLine=true,label={Text(tr("Preset name","预设名称"))})
                TextButton({val p=state.copy(id=java.util.UUID.randomUUID().toString(),name=name.trim());onPresets((presets+p).takeLast(100));state=p},enabled=name.isNotBlank()){Text(tr("Save","保存"))}
            }
            presets.firstOrNull{it.id==state.id}?.let{saved->Row{
                TextButton({onPresets(presets.map{if(it.id==saved.id)it.copy(favorite=!it.favorite)else it})}){Text(if(saved.favorite) "★" else "☆")}
                TextButton({onPresets(presets.filterNot{it.id==saved.id})}){Text(tr("Delete preset","删除预设"))}
            }}
        }
    },confirmButton={TextButton({onApply(effect,composite)},enabled=!copyBusy){Text(tr("Apply","应用"))}},dismissButton={TextButton(onCancel,enabled=!copyBusy){Text(tr("Cancel","取消"))}})
}
