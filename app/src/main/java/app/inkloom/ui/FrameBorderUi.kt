package app.inkloom.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.FrameBorder
import app.inkloom.document.FrameBorderEdits
import app.inkloom.document.FrameDivision
import app.inkloom.document.FrameGeometry
import app.inkloom.document.FrameNames
import app.inkloom.document.InkPage
import app.inkloom.document.PreparedPageGeometry
import app.inkloom.settings.AppSettings
import app.inkloom.settings.text
import kotlin.math.roundToInt

/** CSP's frame border commands. */
internal enum class FrameAction { CREATE, DIVIDE_EQUALLY, COMBINE, DELETE_SELECTED, SETTINGS, REMOVE_BORDER }

/**
 * Binds the frame tool to the active frame border folder and runs frame commands. [runEdit]
 * prepares and applies an undoable page edit, then calls back with the page after it.
 */
@Composable
internal fun FrameBorderHost(view: InkCanvasView, page: InkPage, activeLayerId: String, settings: AppSettings, assets: DocumentAssetService,
                             drawingColor: Int, action: FrameAction?, onActionDone: () -> Unit,
                             runEdit: ((InkPage) -> PreparedPageGeometry, (InkPage) -> Unit) -> Unit, selectLayer: (String) -> Unit) {
    fun tr(en: String, zh: String) = settings.language.text(en, zh)
    val context = LocalContext.current
    val names = FrameNames(tr("Frame", "分格"), tr("Layer 1", "图层 1"), tr("Frame border", "分格框线"))
    val folder = activeFrameFolder(page.layers, activeLayerId)
    fun edit(folderId: String, division: FrameDivision = FrameDivision.BORDER_ONLY, change: (FrameBorder) -> FrameBorder) =
        runEdit({ current -> FrameBorderEdits.update(assets, current, folderId, change(requireNotNull(current.layers.first { it.id == folderId }.frame)), names, division) }) {}
    SideEffect {
        view.frameTool.mode = settings.frameToolMode
        view.frameTool.folderId = folder?.id
        view.frameTool.frames = folder?.frame?.frames.orEmpty()
        view.onFrameEdit = { folderId, request ->
            when (request) {
                is FrameEditRequest.Divide -> edit(folderId, settings.frameDivision) { border ->
                    val index = FrameGeometry.frameAt(border.frames, request.x1, request.y1)
                        ?: FrameGeometry.frameAt(border.frames, (request.x1 + request.x2) / 2f, (request.y1 + request.y2) / 2f)
                        ?: throw IllegalArgumentException(tr("Start dividing inside a frame", "请从分格内开始分割"))
                    // A cut that runs across the page leaves a top–bottom gutter; a cut down the page a left–right one.
                    val across = kotlin.math.abs((request.x2 - request.x1) * page.width) >= kotlin.math.abs((request.y2 - request.y1) * page.height)
                    val pieces = FrameGeometry.divide(border.frames[index], request.x1, request.y1, request.x2, request.y2,
                        if (across) settings.frameVerticalGutter else settings.frameHorizontalGutter, page.width, page.height)
                        ?: throw IllegalArgumentException(tr("The line must cross the frame", "分割线必须穿过分格"))
                    border.copy(frames = border.frames.take(index) + pieces + border.frames.drop(index + 1))
                }
                is FrameEditRequest.AddRectangle -> edit(folderId) { border ->
                    border.copy(frames = border.frames + FrameGeometry.rectangle(request.left, request.top, request.right, request.bottom))
                }
            }
        }
    }
    val target = folder
    when (action) {
        null -> Unit
        FrameAction.CREATE -> {
            var lineWidth by remember { mutableFloatStateOf(6f) }
            var margin by remember { mutableFloatStateOf((minOf(page.width, page.height) * .06f).roundToInt().toFloat()) }
            var drawBorder by remember { mutableStateOf(true) }
            StudioDialog(onDismissRequest = onActionDone, title = { Text(tr("New frame border folder", "新建分格文件夹")) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StudioCheckRow(tr("Draw border", "描绘框线"), drawBorder, { drawBorder = it })
                    StudioSliderRow(tr("Line width", "线宽"), lineWidth, .5f..60f, { lineWidth = it }, unit = "px")
                    StudioSliderRow(tr("Margin", "页边距"), margin, 0f..minOf(page.width, page.height) / 3f, { margin = it }, unit = "px")
                }
            }, confirmButton = { TextButton({
                var after: InkPage? = null
                runEdit({ current ->
                    FrameBorderEdits.create(assets, current, activeLayerId, FrameBorder(listOf(FrameGeometry.pageFrame(current.width, current.height, margin)),
                        drawBorder = drawBorder, lineWidth = lineWidth, colorArgb = drawingColor), names).also { after = it.edit.after }
                }) { _ -> after?.let { updated ->
                    // Draw on the new folder's content layer straight away.
                    updated.layers.firstOrNull { layer -> page.layers.none { it.id == layer.id } && layer.parentId != null && layer.frameBorderOf == null }?.id?.let(selectLayer)
                } }
                onActionDone()
            }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onActionDone) { Text(tr("Cancel", "取消")) } })
        }
        else -> if (target == null) {
            SideEffect { Toast.makeText(context, tr("Select a frame border folder first", "请先选择分格文件夹"), Toast.LENGTH_SHORT).show(); onActionDone() }
        } else when (action) {
            FrameAction.DIVIDE_EQUALLY -> {
                var columns by remember { mutableFloatStateOf(2f) }
                var rows by remember { mutableFloatStateOf(2f) }
                StudioDialog(onDismissRequest = onActionDone, title = { Text(tr("Divide frame border equally", "等分分格")) }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudioSliderRow(tr("Columns", "列数"), columns, 1f..10f, { columns = it.roundToInt().toFloat() }, decimals = 0)
                        StudioSliderRow(tr("Rows", "行数"), rows, 1f..10f, { rows = it.roundToInt().toFloat() }, decimals = 0)
                        StudioNote(if (view.frameTool.selected.isEmpty()) tr("Divides every frame in the folder.", "等分文件夹中的所有分格。")
                            else tr("Divides the selected frames.", "等分选中的分格。"))
                    }
                }, confirmButton = { TextButton({
                    val chosen = view.frameTool.selected.toSet()
                    edit(target.id, settings.frameDivision) { border ->
                        border.copy(frames = border.frames.flatMap { frame ->
                            if (chosen.isNotEmpty() && frame.id !in chosen) listOf(frame)
                            else FrameGeometry.divideEqually(frame, columns.roundToInt(), rows.roundToInt(), settings.frameHorizontalGutter,
                                settings.frameVerticalGutter, page.width, page.height)
                        })
                    }
                    onActionDone()
                }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onActionDone) { Text(tr("Cancel", "取消")) } })
            }
            FrameAction.COMBINE, FrameAction.DELETE_SELECTED -> SideEffect {
                val chosen = view.frameTool.selected.toSet()
                val frames = target.frame?.frames.orEmpty()
                when {
                    action == FrameAction.COMBINE && chosen.size < 2 ->
                        Toast.makeText(context, tr("Select two or more frames with the frame tool", "请用分格工具选择两个以上分格"), Toast.LENGTH_SHORT).show()
                    action == FrameAction.DELETE_SELECTED && (chosen.isEmpty() || chosen.size >= frames.size) ->
                        Toast.makeText(context, tr("Select the frames to delete; one must remain", "请选择要删除的分格，至少保留一个"), Toast.LENGTH_SHORT).show()
                    action == FrameAction.COMBINE -> edit(target.id) { border ->
                        val combined = FrameGeometry.combine(border.frames.filter { it.id in chosen })
                        val first = border.frames.indexOfFirst { it.id in chosen }
                        border.copy(frames = border.frames.filterIndexed { i, f -> f.id !in chosen || i == first }.map { if (it.id == combined.id) combined else it })
                    }
                    else -> edit(target.id) { border -> border.copy(frames = border.frames.filterNot { it.id in chosen }) }
                }
                view.frameTool.selected.clear()
                onActionDone()
            }
            FrameAction.SETTINGS -> {
                val border = requireNotNull(target.frame)
                var lineWidth by remember { mutableFloatStateOf(border.lineWidth) }
                var drawBorder by remember { mutableStateOf(border.drawBorder) }
                var useAsRuler by remember { mutableStateOf(border.useAsRuler) }
                var recolour by remember { mutableStateOf(false) }
                StudioDialog(onDismissRequest = onActionDone, title = { Text(tr("Frame border settings", "分格框线设置")) }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudioCheckRow(tr("Draw border", "描绘框线"), drawBorder, { drawBorder = it })
                        StudioSliderRow(tr("Line width", "线宽"), lineWidth, .5f..60f, { lineWidth = it }, unit = "px")
                        StudioCheckRow(tr("Use the drawing color", "使用描绘色"), recolour, { recolour = it })
                        StudioCheckRow(tr("Snap to frames (use as ruler)", "吸附分格（用作尺规）"), useAsRuler, { useAsRuler = it })
                    }
                }, confirmButton = { TextButton({
                    edit(target.id) { it.copy(lineWidth = lineWidth, drawBorder = drawBorder, useAsRuler = useAsRuler, colorArgb = if (recolour) drawingColor else it.colorArgb) }
                    onActionDone()
                }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onActionDone) { Text(tr("Cancel", "取消")) } })
            }
            FrameAction.REMOVE_BORDER -> SideEffect {
                // The folder and its layers stay; only the frames, mask and border layer go.
                runEdit({ current -> PreparedPageGeometry(FrameBorderEdits.removeBorder(current, target.id)) {} }) {}
                onActionDone()
            }
            else -> SideEffect(onActionDone)
        }
    }
}
