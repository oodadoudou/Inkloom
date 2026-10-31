package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.inkloom.document.Material
import app.inkloom.document.MaterialKind
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CSP's material palette, kept simple: register the current layer as an image material or the
 * page as a template material, then place images on a new layer or add pages from templates.
 */
@Composable
internal fun MaterialsDialog(materials: List<Material>, language: UiLanguage, busy: Boolean, message: String?,
    onRegisterLayer: (String) -> Unit, onRegisterPage: (String) -> Unit, onPlace: (Material) -> Unit,
    onAddPage: (Material) -> Unit, onDelete: (Material) -> Unit, onDismiss: () -> Unit,
    onRegisterAnimation: ((String) -> Unit)? = null) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var name by remember { mutableStateOf("") }
    StudioDialog(onDismissRequest = onDismiss, wide = true, title = { Text(tr("Materials", "素材")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioSection(tr("Register", "登记"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioTextField(name, { name = it.take(80) }, tr("Material name", "素材名称"), Modifier.weight(1f))
                StudioButton(tr("Current layer", "当前图层"), { onRegisterLayer(name.trim().ifBlank { tr("Image", "图像") }); name = "" }, enabled = !busy)
                StudioButton(tr("This page", "本页"), { onRegisterPage(name.trim().ifBlank { tr("Template", "模板") }); name = "" }, enabled = !busy)
                onRegisterAnimation?.let { register -> StudioButton(tr("Animation", "动画"), { register(name.trim().ifBlank { tr("Animation", "动画") }); name = "" }, enabled = !busy) }
            }
            message?.let { StudioNote(it) }
            StudioSection(tr("Library", "素材库"))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(materials.sortedByDescending { it.createdAt }, key = { it.id }) { material ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MaterialThumbnail(material)
                        Column(Modifier.weight(1f)) {
                            Text(material.name, style = MaterialTheme.typography.bodyMedium)
                            StudioNote(when (material.kind) {
                                MaterialKind.IMAGE -> tr("Image · ${material.imageWidth} × ${material.imageHeight} px", "图像 · ${material.imageWidth} × ${material.imageHeight} px")
                                MaterialKind.TEMPLATE -> tr("Page template with layers", "含图层的页面模板")
                                MaterialKind.ANIMATION -> tr("Animation · ${material.page?.layers?.firstOrNull()?.animation?.cels?.size ?: 0} cels", "动画 · ${material.page?.layers?.firstOrNull()?.animation?.cels?.size ?: 0} 个赛璐")
                            })
                        }
                        if (material.kind == MaterialKind.TEMPLATE) StudioButton(tr("Add page", "添加页面"), { onAddPage(material) })
                        else StudioButton(tr("Place", "置入"), { onPlace(material) })
                        StudioButton(tr("Delete", "删除"), { onDelete(material) })
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(tr("Done", "完成")) } })
}

@Composable
private fun MaterialThumbnail(material: Material) {
    val bitmap by produceState<Bitmap?>(null, material.imageAssetPath) {
        value = material.imageAssetPath?.let { path -> withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
            val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / it <= 96 }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } }
    }
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
        else if (material.kind == MaterialKind.TEMPLATE) Icon(Icons.Outlined.Description, null)
        else if (material.kind == MaterialKind.ANIMATION) Icon(Icons.Outlined.Movie, null)
    }
}
