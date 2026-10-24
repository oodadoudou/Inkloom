package app.inkloom.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.inkloom.color.*
import app.inkloom.document.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.*

@Composable
internal fun ColorManagementDialog(page:InkPage,language:UiLanguage,busy:Boolean,onDismiss:()->Unit,onApply:(PageColorManagement)->Unit) {
    fun tr(en:String,zh:String)=language.text(en,zh)
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val assets=remember{DocumentAssetService(context)}
    var state by remember{mutableStateOf(page.colorManagement)}
    var slot by remember{mutableIntStateOf(0)}
    var importing by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri-> if(uri!=null) {
        importing=true
        scope.launch { try {
            val path=withContext(Dispatchers.IO){assets.importColorProfile(uri,slot<2)}
            if(slot==3) require(NativeColorManagement.profileKind(IccColorPipeline.profile(path))==2) { tr("Choose a CMYK printer profile","请选择 CMYK 印刷配置") }
            state=when(slot){0->state.copy(assignedProfile=path);1->state.copy(outputProfile=path);2->state.copy(proofProfile=path);else->state.copy(cmykProfile=path)}
            error=null
        } catch(failure:Throwable){error=failure.message} finally{importing=false} }
    } }
    StudioDialog(onDismissRequest={if(!busy && !importing)onDismiss()},title={Text(tr("Color management","色彩管理"))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            var intentMenu by remember{mutableStateOf(false)}
            fun intentName(intent:ColorRenderingIntent)=when(intent){ColorRenderingIntent.PERCEPTUAL->tr("Perceptual","可感知");ColorRenderingIntent.RELATIVE->tr("Relative colorimetric","相对比色")
                ColorRenderingIntent.SATURATION->tr("Saturation","饱和度");ColorRenderingIntent.ABSOLUTE->tr("Absolute colorimetric","绝对比色")}
            StudioCheckRow(tr("Wide gamut · 16-bit float","宽色域 · 16 位浮点"),state.highPrecision,{state=state.copy(highPrecision=it)})
            StudioNote(tr("Editable pixels stay in linear sRGB. Profile changes affect presentation and output.","可编辑像素保存在线性 sRGB；配置影响显示与输出。"))
            StudioSection(tr("Profiles","配置文件"))
            listOf(tr("Assign RGB profile","指定 RGB 配置"),tr("Convert output profile","转换输出配置"),tr("Proof profile","软打样配置"),tr("CMYK printer profile","CMYK 印刷配置")).forEachIndexed { i,label ->
                val path=when(i){0->state.assignedProfile;1->state.outputProfile;2->state.proofProfile;else->state.cmykProfile}
                Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,Modifier.width(132.dp),style=MaterialTheme.typography.bodySmall,maxLines=2)
                    Text(path?.substringAfterLast('/')?:tr("None","无"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,
                        color=if(path!=null) MaterialTheme.colorScheme.onSurface else studioMuted(),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    StudioButton(tr("Choose…","选择…"),{slot=i;picker.launch(arrayOf("application/vnd.iccprofile","application/octet-stream","*/*"))},enabled=!busy && !importing)
                    if(path!=null) StudioButton(tr("Reset","重置"),{state=when(i){0->state.copy(assignedProfile=null);1->state.copy(outputProfile=null);2->state.copy(proofProfile=null,softProof=false);else->state.copy(cmykProfile=null)}})
                }
            }
            StudioSection(tr("Output","输出"))
            StudioSegmented(listOf("sRGB","Display P3"),RgbOutputSpace.entries.indexOf(state.outputSpace),{state=state.copy(outputSpace=RgbOutputSpace.entries[it],outputProfile=null)},label=tr("RGB space","RGB 空间"))
            StudioDropdownField(intentName(state.intent),intentMenu,{intentMenu=it},label=tr("Rendering intent","渲染意图")) {
                ColorRenderingIntent.entries.forEach { intent -> StudioMenuItem(text={Text(intentName(intent))},onClick={state=state.copy(intent=intent);intentMenu=false}) }
            }
            StudioCheckRow(tr("Soft proof preview","软打样预览"),state.softProof,{state=state.copy(softProof=it)},enabled=state.proofProfile!=null)
            StudioCheckRow(tr("Gamut warning","色域警告"),state.gamutWarning,{state=state.copy(gamutWarning=it)},enabled=state.proofProfile!=null && state.softProof)
            StudioNote(tr("PNG embeds RGB ICC. CMYK TIFF requires a printer profile and includes paper.","PNG 嵌入 RGB ICC；CMYK TIFF 需要印刷配置并包含纸张。"),Modifier.padding(top=4.dp))
            error?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton({onApply(state)},enabled=!busy && !importing){Text(tr("Apply","应用"))}},dismissButton={TextButton(onDismiss,enabled=!busy && !importing){Text(tr("Cancel","取消"))}})
}
