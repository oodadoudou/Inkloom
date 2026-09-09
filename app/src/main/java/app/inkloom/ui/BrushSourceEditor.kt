package app.inkloom.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.inkloom.brushpresetv5.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BrushSourceEditor(preset:BrushPresetV5,role:BrushSourceRole,workspace:BrushSourceWorkspace,language:UiLanguage,
    onDismiss:()->Unit,onApply:(BrushPresetV5)->Unit) {
    fun tr(en:String,zh:String)=language.text(en,zh)
    var draft by remember { mutableStateOf(preset) }
    var index by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val grain=role in setOf(BrushSourceRole.GRAIN,BrushSourceRole.SECONDARY_GRAIN)
    fun paths()=when(role) {
        BrushSourceRole.SHAPE -> draft.tipTexture?.let { it.assetPaths.ifEmpty { listOfNotNull(it.assetPath) } }.orEmpty()
        BrushSourceRole.GRAIN -> listOfNotNull(draft.textureGrain?.assetPath)
        BrushSourceRole.SECONDARY_SHAPE -> draft.dual?.tipAssetPaths.orEmpty()
        BrushSourceRole.SECONDARY_GRAIN -> listOfNotNull(draft.dual?.textureGrain?.assetPath)
    }
    fun edit(operation:()->String) {
        busy=true;failure=null
        scope.launch {
            try {
                val path=withContext(Dispatchers.IO) {
                    val result=operation()
                    if(!grain) {
                        val mask=app.inkloom.engine.v5.material.AndroidBrushTipMaskDecoder.decode(app.inkloom.engine.v5.material.BrushTipMaskRequest(result,1f))
                        require(mask!=null && mask.maximumCoverage>0f) { "Empty shape / 笔尖图案为空" }
                    }
                    result
                }
                require(draft.identity.family != BrushFamily.AIRBRUSH) { "Airbrush image sources are not supported / 喷枪暂不支持图片来源" }
                val candidate=BrushSourceWorkspace.replace(draft,role,path,index)
                val resolution=BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(candidate))
                require(resolution==BrushExecutionCapabilityResolution.Supported) { "${tr("Unsupported combination", "不支持此组合")}: $resolution" }
                draft=candidate
            } catch(e:Exception) { failure=e.message ?: tr("Could not edit source", "无法编辑来源") }
            finally { busy=false }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)edit { workspace.import(uri) } }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(tr("Source editor","来源编辑"))},
        text={Column(Modifier.width(440.dp).heightIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(tr("Edits use a copy. Images larger than 2048 px are reduced for brush use.","使用独立副本编辑。超过 2048 px 的图片会缩小为笔刷素材。"))
            val group=when(role) {
                BrushSourceRole.SHAPE -> BrushOptionGroup.TIP
                BrushSourceRole.GRAIN -> BrushOptionGroup.PAPER
                BrushSourceRole.SECONDARY_SHAPE -> BrushOptionGroup.DUAL_SHAPE
                BrushSourceRole.SECONDARY_GRAIN -> BrushOptionGroup.DUAL_GRAIN
            }
            BrushSourcePreviews(draft,group,language)
            if(!grain && paths().size>1) Row { paths().indices.forEach { i -> TextButton(enabled=!busy,onClick={index=i}) { Text(if(i==index) "● ${i+1}" else "${i+1}") } } }
            TextButton(enabled=!busy,onClick={picker.launch(arrayOf("image/*"))}) { Text(tr("Import / replace image","导入／更换图片")) }
            if(!grain) TextButton(enabled=!busy,onClick={index=paths().size;picker.launch(arrayOf("image/*"))}) { Text(tr("Add shape image","添加笔尖图片")) }
            Row {
                TextButton(enabled=!busy && paths().getOrNull(index)!=null,onClick={val path=paths()[index];edit{workspace.transform(path,SourceImageOperation.ROTATE)}}) { Text(tr("Rotate 90°","旋转 90°")) }
                TextButton(enabled=!busy && paths().getOrNull(index)!=null,onClick={val path=paths()[index];edit{workspace.transform(path,SourceImageOperation.INVERT)}}) { Text(tr("Invert","反转")) }
            }
            if(grain) TextButton(enabled=!busy && paths().getOrNull(index)!=null,onClick={val path=paths()[index];edit{workspace.transform(path,SourceImageOperation.REPEAT)}}) { Text(tr("Blend repeating edges","平铺边缘融合")) }
            if(role==BrushSourceRole.SHAPE && paths().isNotEmpty()) {
                Row { Text(tr("Alpha threshold","透明度阈值"),Modifier.weight(1f));Switch(draft.tipTexture?.alphaThreshold!=null,{enabled -> draft=draft.copy(tipTexture=(draft.tipTexture ?: TipTextureSettings()).copy(alphaThreshold=if(enabled).5f else null))},enabled=!busy) }
                draft.tipTexture?.alphaThreshold?.let { value -> Slider(value,{draft=draft.copy(tipTexture=draft.tipTexture!!.copy(alphaThreshold=it))},enabled=!busy) }
            }
            if(busy) CircularProgressIndicator()
            failure?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }},
        confirmButton={TextButton(enabled=!busy && failure==null,onClick={onApply(draft)}){Text(tr("Apply source","应用来源"))}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(tr("Cancel","取消"))}})
}
