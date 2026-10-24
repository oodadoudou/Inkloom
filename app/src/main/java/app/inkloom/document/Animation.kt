package app.inkloom.document

import kotlinx.serialization.Serializable
import java.util.UUID

/** One cel on the timeline: a layer of the animation folder shown for [frames] frames. */
@Serializable
data class AnimationCel(val layerId: String, val frames: Int = 1) {
    init { require(frames in 1..240) { "A cel lasts 1 to 240 frames" } }
}

/**
 * An animation folder (CSP's animation folder with its timeline): the cels play in order at
 * [frameRate] frames per second; only the [current] cel is shown on the canvas.
 */
@Serializable
data class AnimationFolder(val frameRate: Int = 12, val cels: List<AnimationCel> = emptyList(), val current: String? = null,
    /** CSP's onion skin: neighbouring cels shown faintly while drawing; null when off. */
    val onionSkin: OnionSkin? = null) {
    init { require(frameRate in 1..60) { "1 to 60 frames per second" } }
    val totalFrames: Int get() = cels.sumOf { it.frames }
}

/**
 * Onion skin settings, as CSP's Timeline offers them: how many cels before and after the current one
 * show, and how strongly. Previous cels are tinted blue and next cels green; nearer cels show stronger.
 */
@Serializable
data class OnionSkin(val before: Int = 1, val after: Int = 1, val opacity: Float = .35f) {
    companion object { const val MAX_CELS = 5 }
}

/** How an onion-skin cel shows on the canvas: display only, never in exports, fills or the eyedropper. */
data class OnionSkinCel(val opacity: Float, val tint: LayerColorReplacement)

object AnimationOnionSkin {
    val PREVIOUS = LayerColorReplacement(darkArgb = 0xff2f62e0L, lightArgb = 0xffffffffL)
    val NEXT = LayerColorReplacement(darkArgb = 0xff23a046L, lightArgb = 0xffffffffL)

    /** The onion-skin cels of every visible animation folder with onion skin on, by layer id. */
    fun cels(layers: List<InkLayer>): Map<String, OnionSkinCel> = buildMap {
        layers.forEach { folder ->
            val animation = folder.animation ?: return@forEach
            val skin = animation.onionSkin ?: return@forEach
            if (!LayerHierarchy.effectiveVisible(layers, folder)) return@forEach
            val index = animation.cels.indexOfFirst { it.layerId == animation.current }
            if (index < 0) return@forEach
            val opacity = skin.opacity.takeIf { it.isFinite() }?.coerceIn(.05f, 1f) ?: return@forEach
            fun add(count: Int, step: Int, tint: LayerColorReplacement) {
                val n = count.coerceIn(0, OnionSkin.MAX_CELS)
                for (k in 1..n) {
                    val cel = animation.cels.getOrNull(index + step * k) ?: break
                    val layer = layers.firstOrNull { it.id == cel.layerId } ?: continue
                    if (cel.layerId == animation.current || cel.layerId in this) continue
                    put(cel.layerId, OnionSkinCel(opacity * layer.opacity * (n - k + 1) / n, tint))
                }
            }
            add(skin.before, -1, PREVIOUS)
            add(skin.after, 1, NEXT)
        }
    }
}

/** Display names for what animation commands create, in the user's language. */
data class AnimationNames(val folder: String = "Animation", val cel: String = "Cel")

/** Page edits for animation folders; every result is a whole new page, applied as one undo step. */
object AnimationEdits {
    /** A new animation folder above [targetId] with its first cel, which becomes the current cel. */
    fun createFolder(page: InkPage, targetId: String, names: AnimationNames): Pair<InkPage, String> {
        val target = page.layers.firstOrNull { it.id == targetId }
        val parentId = target?.let { if (it.role == LayerRole.FOLDER && it.animation == null) it.id else it.parentId }
        val folder = InkLayer(name = nextName(page, names.folder), role = LayerRole.FOLDER, parentId = parentId)
        val cel = InkLayer(name = "${names.cel} 1", parentId = folder.id)
        val position = target?.let { t -> page.layers.indexOfLast { it.id == t.id || isInside(page.layers, it, t.id) } + 1 } ?: page.layers.size
        val layers = page.layers.toMutableList().apply { addAll(position.coerceIn(0, size), listOf(folder.copy(animation = AnimationFolder(cels = listOf(AnimationCel(cel.id)), current = cel.id)), cel)) }
        return page.copy(layers = layers) to cel.id
    }

    /** A new cel after the current one, shown straight away. */
    fun addCel(page: InkPage, folderId: String, names: AnimationNames): Pair<InkPage, String>? {
        val folder = page.layers.firstOrNull { it.id == folderId }?.animation ?: return null
        val cel = InkLayer(name = "${names.cel} ${folder.cels.size + 1}", parentId = folderId)
        val after = folder.current?.let { current -> folder.cels.indexOfFirst { it.layerId == current } } ?: (folder.cels.size - 1)
        val cels = folder.cels.toMutableList().apply { add(after + 1, AnimationCel(cel.id)) }
        val lastChild = page.layers.indexOfLast { isInside(page.layers, it, folderId) }.let { if (it < 0) page.layers.indexOfFirst { l -> l.id == folderId } else it }
        val layers = page.layers.toMutableList().apply { add(lastChild + 1, cel) }
        return show(page.copy(layers = layers.map { if (it.id == folderId) it.copy(animation = folder.copy(cels = cels)) else it }), folderId, cel.id) to cel.id
    }

    /** Shows [celId] and hides the folder's other cels, as CSP does for the current frame. */
    fun show(page: InkPage, folderId: String, celId: String): InkPage {
        val folder = page.layers.firstOrNull { it.id == folderId }?.animation ?: return page
        val celIds = folder.cels.mapTo(hashSetOf()) { it.layerId }
        return page.copy(layers = page.layers.map { layer ->
            when {
                layer.id == folderId -> layer.copy(animation = folder.copy(current = celId))
                layer.id in celIds -> layer.copy(visible = layer.id == celId)
                else -> layer
            }
        })
    }

    /** The cel shown at [frame] (0-based) of the timeline. */
    fun celAt(folder: AnimationFolder, frame: Int): AnimationCel? {
        var remaining = frame.mod(folder.totalFrames.coerceAtLeast(1))
        folder.cels.forEach { cel -> if (remaining < cel.frames) return cel; remaining -= cel.frames }
        return folder.cels.lastOrNull()
    }

    fun update(page: InkPage, folderId: String, change: (AnimationFolder) -> AnimationFolder): InkPage =
        page.copy(layers = page.layers.map { if (it.id == folderId && it.animation != null) it.copy(animation = change(it.animation)) else it })

    fun setFrames(page: InkPage, folderId: String, celId: String, frames: Int) = update(page, folderId) { folder ->
        folder.copy(cels = folder.cels.map { if (it.layerId == celId) it.copy(frames = frames.coerceIn(1, 240)) else it })
    }

    fun move(page: InkPage, folderId: String, celId: String, by: Int) = update(page, folderId) { folder ->
        val index = folder.cels.indexOfFirst { it.layerId == celId }
        val target = (index + by).coerceIn(0, folder.cels.size - 1)
        if (index < 0 || target == index) folder else folder.copy(cels = folder.cels.toMutableList().apply { add(target, removeAt(index)) })
    }

    /** Takes a cel off the timeline; its layer stays in the folder, shown. */
    fun removeFromTimeline(page: InkPage, folderId: String, celId: String): InkPage = update(page, folderId) { folder ->
        val cels = folder.cels.filterNot { it.layerId == celId }
        folder.copy(cels = cels, current = folder.current.takeIf { it != celId } ?: cels.firstOrNull()?.layerId)
    }.let { updated -> updated.copy(layers = updated.layers.map { if (it.id == celId) it.copy(visible = true) else it }) }

    /** ED-22: the animation folder and everything in it, as a page for an animation material. */
    fun subtree(page: InkPage, folderId: String): InkPage {
        val folder = requireNotNull(page.layers.firstOrNull { it.id == folderId && it.animation != null }) { "Choose an animation folder" }
        val layers = page.layers.filter { it.id == folderId || isInside(page.layers, it, folderId) }.map { if (it.id == folder.id) it.copy(parentId = null) else it }
        val ids = layers.mapTo(hashSetOf()) { it.id }
        return InkPage(id = UUID.randomUUID().toString(), title = folder.name, width = page.width, height = page.height, dpi = page.dpi,
            layers = layers, elements = page.elements.filter { it.layerId in ids })
    }

    /** Places an animation material's folder on [page], above everything, with new IDs. */
    fun place(page: InkPage, template: InkPage): InkPage {
        val ids = template.layers.associate { it.id to UUID.randomUUID().toString() }
        val layers = template.layers.map { layer ->
            layer.copy(id = ids.getValue(layer.id), parentId = layer.parentId?.let(ids::get),
                animation = layer.animation?.let { folder -> folder.copy(cels = folder.cels.mapNotNull { cel -> ids[cel.layerId]?.let { cel.copy(layerId = it) } },
                    current = folder.current?.let(ids::get)) })
        }
        val elements = template.elements.mapNotNull { element -> ids[element.layerId]?.let { MaterialStore.withLayer(element, it) } }
        return page.copy(layers = page.layers + layers, elements = page.elements + elements)
    }

    private fun isInside(layers: List<InkLayer>, layer: InkLayer, folderId: String): Boolean {
        var parent = layer.parentId
        while (parent != null) { if (parent == folderId) return true; parent = layers.firstOrNull { it.id == parent }?.parentId }
        return false
    }

    private fun nextName(page: InkPage, base: String): String {
        val names = page.layers.mapTo(hashSetOf()) { it.name }
        return generateSequence(1) { it + 1 }.map { "$base $it" }.first { it !in names }
    }
}
