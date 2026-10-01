package app.inkloom.settings

import kotlinx.serialization.json.*

/** Read-only boundary for pre-tree settings and old backups. Never used by live panel actions. */
object LegacyWorkspaceImport {
    val panelKeys: Set<String> = buildSet {
        addAll(listOf("showPageRail", "showBrushPanel", "showLayerPanel", "panelTabOrder", "workspaceLayouts", "compactPanelDefaultsApplied", "studioWorkspaceVersion"))
        for (prefix in listOf("page", "brush", "layer")) {
            for (suffix in listOf("Dock", "Mode", "Locked", "XDp", "YDp", "WidthDp", "HeightDp")) add("${prefix}Panel$suffix")
        }
        for (prefix in listOf("left", "right", "bottom", "free")) {
            add("${prefix}DockStacked"); add("${prefix}DockActivePanel")
        }
    }
    val railKeys = setOf("showToolbar", "verticalToolbar", "toolbarDock", "toolbarOffsetXDp", "toolbarOffsetYDp", "toolPaletteLayout")
    private val json = Json { ignoreUnknownKeys = true }

    fun fromPreferences(values: Map<String, *>, viewportWidth:Float=1280f, viewportHeight:Float=800f): PanelWorkspaceBackup {
        val root = buildJsonObject {
            values.forEach { (key, value) -> when (value) {
                is Boolean -> put(key, value)
                is Float -> if (value.isFinite()) put(key, value)
                is Int -> put(key, value)
                is String -> put(key, value)
            } }
        }
        return fromSettings(root,viewportWidth,viewportHeight)
    }

    fun fromSettings(root: JsonObject, viewportWidth:Float=1280f, viewportHeight:Float=800f): PanelWorkspaceBackup {
        val profiles = runCatching {
            val stored = root["workspaceLayouts"] ?: return@runCatching emptyMap()
            val data = if (stored is JsonPrimitive && stored.isString) json.parseToJsonElement(stored.content).jsonObject else stored.jsonObject
            listOf("LANDSCAPE", "PORTRAIT").mapNotNull { orientation ->
                data[orientation.lowercase()]?.jsonObject?.let {
                    val landscape=orientation=="LANDSCAPE"
                    orientation to NamedPanelLayout(orientation, layout(it,
                        if(landscape) maxOf(viewportWidth,viewportHeight) else minOf(viewportWidth,viewportHeight),
                        if(landscape) minOf(viewportWidth,viewportHeight) else maxOf(viewportWidth,viewportHeight)), rail(it))
                }
            }.toMap()
        }.getOrDefault(emptyMap())
        return PanelWorkspaceBackup(layout(root,viewportWidth,viewportHeight), NamedPanelLayouts(), rail(root), profiles)
    }

    private fun layout(root: JsonObject, viewportWidth:Float, viewportHeight:Float): EditorPanelLayout {
        val panels = listOf(EditorPanel.PAGES, EditorPanel.BRUSHES, EditorPanel.LAYERS)
        val defaults = EditorPanelLayout()
        val names = mapOf(EditorPanel.PAGES to "page", EditorPanel.BRUSHES to "brush", EditorPanel.LAYERS to "layer")
        val openKeys = mapOf(EditorPanel.PAGES to "showPageRail", EditorPanel.BRUSHES to "showBrushPanel", EditorPanel.LAYERS to "showLayerPanel")
        var result = defaults.copy(floating = defaults.floating.map { floating ->
            val panel = (floating.node as PanelNode.Tabs).panels.first()
            val prefix = names[panel] ?: return@map floating
            floating.copy(rect = PanelRect(root.number("${prefix}PanelXDp", floating.rect.x), root.number("${prefix}PanelYDp", floating.rect.y),
                root.number("${prefix}PanelWidthDp", floating.rect.width).coerceIn(72f, 1200f), root.number("${prefix}PanelHeightDp", floating.rect.height).coerceIn(72f, 1600f)))
        }, open = panels.filterTo(linkedSetOf()) { root.flag(openKeys.getValue(it), false) })
        val originalRects=panels.associateWith { panel -> result.floating.single { panel in EditorPanelLayouts.members(it.node) }.rect }
        val order = root.text("panelTabOrder").split(',').mapNotNull { value -> panels.firstOrNull { it.name == value.trim() } }.distinct().let { it + panels.filterNot(it::contains) }
        for (dock in listOf("LEFT", "RIGHT", "BOTTOM", "FREE")) {
            val members = order.filter { root.text("${names.getValue(it)}PanelDock").ifEmpty { "FREE" } == dock }
            if (dock == "FREE" && !root.flag("freeDockStacked", false)) continue
            var targetId: String? = null
            for (panel in members) {
                val owner = result.floating.firstOrNull { panel in EditorPanelLayouts.members(it.node) } ?: continue
                val stacked = root.flag("${dock.lowercase()}DockStacked", false)
                val target = if (stacked && targetId != null) PanelDropTarget(targetId, PanelEdge.TAB, Int.MAX_VALUE)
                    else if (dock != "FREE") PanelDropTarget(targetId ?: "canvas", if (targetId == null) PanelEdge.valueOf(dock) else PanelEdge.BOTTOM) else null
                val splitId="split-${result.nextId}"
                val destination=target?.let { EditorPanelLayouts.node(result,it.nodeId) }
                val destinationRect=target?.let { drop -> EditorPanelLayouts.slots(result,viewportWidth,viewportHeight).firstOrNull { it.node.id==drop.nodeId }?.rect }
                result = EditorPanelLayouts.move(result, owner.node.id, null, target, owner.rect)
                if(target!=null && target.edge!=PanelEdge.TAB && destination!=null) {
                    val horizontal=target.edge in setOf(PanelEdge.LEFT,PanelEdge.RIGHT)
                    val desired=if(horizontal) members.maxOf { originalRects.getValue(it).width } else owner.rect.height
                    val extent=if(horizontal) destinationRect?.width ?: viewportWidth else destinationRect?.height ?: viewportHeight
                    val portion=if(EditorPanelLayouts.containsCanvas(destination)) (desired+2f)/extent.coerceAtLeast(1f)
                        else desired/(desired+EditorPanelLayouts.members(destination).mapNotNull(originalRects::get).maxOfOrNull { if(horizontal) it.width else it.height }.let { it ?: desired })
                    val before=target.edge in setOf(PanelEdge.LEFT,PanelEdge.TOP)
                    result=EditorPanelLayouts.update(result,splitId) { node -> (node as PanelNode.Split).copy(
                        fraction=(if(before) portion else 1f-portion).coerceIn(.05f,.95f)) }
                }
                targetId = tabsId(result, panel)
            }
            if (root.flag("${dock.lowercase()}DockStacked", false)) {
                val active = members.firstOrNull { it.name == root.text("${dock.lowercase()}DockActivePanel") }
                if (active != null) result = EditorPanelLayouts.select(result, active)
            }
        }
        // Former per-panel locks had no tree equivalent. Preserve the safer group lock.
        result = result.copy(locked = names.values.any { root.flag("${it}PanelLocked", false) })
        require(EditorPanelLayouts.isValid(result)) { "Invalid imported legacy workspace" }
        return result
    }

    private fun tabsId(layout: EditorPanelLayout, panel: EditorPanel): String? {
        fun find(node: PanelNode): String? = when (node) {
            is PanelNode.Tabs -> node.id.takeIf { panel in node.panels }
            is PanelNode.Split -> find(node.first) ?: find(node.second)
            else -> null
        }
        return find(layout.dock) ?: layout.floating.firstNotNullOfOrNull { find(it.node) }
    }
    private fun rail(root: JsonObject) = ToolRailLayout(root.flag("showToolbar", true), root.flag("verticalToolbar", true),
        root.text("toolbarDock").takeIf { value -> ToolbarDock.entries.any { it.name == value } } ?: ToolbarDock.LEFT.name,
        root.number("toolbarOffsetXDp", 16f), root.number("toolbarOffsetYDp", 16f),
        ToolPaletteLayout.encode(ToolPaletteLayout.decode(root.text("toolPaletteLayout"))))
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()
    private fun JsonObject.flag(key: String, fallback: Boolean) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: fallback
    private fun JsonObject.number(key: String, fallback: Float) = (get(key) as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite)?.coerceIn(-2400f, 2400f) ?: fallback
}
