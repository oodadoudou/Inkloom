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
    AlertDialog(onDismissRequest={if(!busy && !importing)onDismiss()},title={Text(tr("Color management","色彩管理"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row { Checkbox(state.highPrecision,{state=state.copy(highPrecision=it)});Text(tr("Wide gamut · 16-bit float","宽色域 · 16 位浮点")) }
            Text(tr("Editable pixels stay in linear sRGB. Profile changes affect presentation and output.","可编辑像素保存在线性 sRGB；配置影响显示与输出。"),style=MaterialTheme.typography.bodySmall)
            listOf(tr("Assign RGB profile","指定 RGB 配置"),tr("Convert output profile","转换输出配置"),tr("Proof profile","软打样配置"),tr("CMYK printer profile","CMYK 印刷配置")).forEachIndexed { i,label ->
                val path=when(i){0->state.assignedProfile;1->state.outputProfile;2->state.proofProfile;else->state.cmykProfile}
                Row {
                    TextButton({slot=i;picker.launch(arrayOf("application/vnd.iccprofile","application/octet-stream","*/*"))},Modifier.weight(1f),enabled=!busy && !importing){Text(label+if(path!=null) " ✓" else "")}
                    if(path!=null) TextButton({state=when(i){0->state.copy(assignedProfile=null);1->state.copy(outputProfile=null);2->state.copy(proofProfile=null,softProof=false);else->state.copy(cmykProfile=null)}}){Text(tr("Reset","重置"))}
                }
            }
            Row { RgbOutputSpace.entries.forEach { space -> FilterChip(state.outputSpace==space,{state=state.copy(outputSpace=space,outputProfile=null)},label={Text(if(space==RgbOutputSpace.SRGB) "sRGB" else "Display P3")}) } }
            Row { Checkbox(state.softProof,{state=state.copy(softProof=it)},enabled=state.proofProfile!=null);Text(tr("Soft proof preview","软打样预览")) }
            ColorRenderingIntent.entries.forEach { intent -> Row {
                RadioButton(state.intent==intent,{state=state.copy(intent=intent)})
                Text(when(intent){ColorRenderingIntent.PERCEPTUAL->tr("Perceptual","可感知");ColorRenderingIntent.RELATIVE->tr("Relative colorimetric","相对比色");ColorRenderingIntent.SATURATION->tr("Saturation","饱和度");ColorRenderingIntent.ABSOLUTE->tr("Absolute colorimetric","绝对比色")})
            } }
            Text(tr("PNG embeds RGB ICC. CMYK TIFF requires a printer profile and includes paper.","PNG 嵌入 RGB ICC；CMYK TIFF 需要印刷配置并包含纸张。"),style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton({onApply(state)},enabled=!busy && !importing){Text(tr("Apply","应用"))}},dismissButton={TextButton(onDismiss,enabled=!busy && !importing){Text(tr("Cancel","取消"))}})
}
