package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PageTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val width: Float = DEFAULT_PAGE_WIDTH,
    val height: Float = DEFAULT_PAGE_HEIGHT,
    val background: PageBackground,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class PageTemplateLibrary(
    val customTemplates: List<PageTemplate> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val notebookDefaults: Map<String, String> = emptyMap(),
) {
    fun allTemplates(): List<PageTemplate> = PageTemplateCatalog.builtIns + customTemplates
    fun template(id: String?): PageTemplate? = id?.let { wanted -> allTemplates().firstOrNull { it.id == wanted } }
    fun defaultFor(documentId: String): PageTemplate = template(notebookDefaults[documentId]) ?: PageTemplateCatalog.blank
}

object PageTemplateCatalog {
    val builtIns: List<PageTemplate> = PaperPattern.entries.map { pattern ->
        PageTemplate(
            id = "builtin-${pattern.name.lowercase()}",
            name = pattern.name.lowercase().replaceFirstChar(Char::uppercase),
            background = PageBackground.Paper(pattern = pattern),
            createdAt = 0L,
        )
    }
    val blank: PageTemplate = builtIns.first { it.background == PageBackground.Paper(pattern = PaperPattern.PLAIN) }
    fun isBuiltIn(id: String): Boolean = builtIns.any { it.id == id }
}

object PageTemplateOperations {
    fun newPage(template: PageTemplate, title: String): InkPage = InkPage(
        title = title,
        width = template.width,
        height = template.height,
        background = template.background,
    )

    /** Existing page content keeps its coordinate system; an empty page adopts the template dimensions. */
    fun apply(page: InkPage, template: PageTemplate): InkPage {
        val empty = page.layers.none { layer ->
            layer.rasterTileSurface != null || !layer.rasterAssetPath.isNullOrBlank()
        } && page.elements.isEmpty()
        return page.copy(
            width = if (empty) template.width else page.width,
            height = if (empty) template.height else page.height,
            background = template.background,
            backgroundRotationQuarterTurns = 0,
            crop = PageCrop(),
            pdfTextBlocks = emptyList(),
            pdfTextIndexVersion = 0,
            pdfGotoLinks = emptyList(),
            pdfLinkIndexVersion = 0,
        )
    }

    fun fromPage(page: InkPage, name: String): PageTemplate = PageTemplate(
        name = name.trim().take(MAX_TEMPLATE_NAME_LENGTH).ifBlank { "My template" },
        width = page.width,
        height = page.height,
        background = page.background,
    )
}

class PageTemplateStore(private val root: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val file = File(root, "page-templates.json")

    @Synchronized
    fun load(): PageTemplateLibrary {
        val decoded = file.takeIf(File::isFile)?.let { source ->
            runCatching { json.decodeFromString<PageTemplateLibrary>(source.readText()) }.getOrNull()
        } ?: PageTemplateLibrary()
        return sanitize(decoded)
    }

    @Synchronized
    fun save(library: PageTemplateLibrary): PageTemplateLibrary = sanitize(library).also { safe ->
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeText(json.encodeToString(safe))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    @Synchronized
    fun add(templates: List<PageTemplate>): PageTemplateLibrary {
        if (templates.isEmpty()) return load()
        val current = load()
        return save(current.copy(customTemplates = (current.customTemplates + templates).distinctBy(PageTemplate::id)))
    }

    @Synchronized
    fun toggleFavorite(templateId: String): PageTemplateLibrary {
        val current = load()
        if (current.template(templateId) == null) return current
        val favorites = current.favoriteIds.toMutableSet().apply { if (!add(templateId)) remove(templateId) }
        return save(current.copy(favoriteIds = favorites))
    }

    @Synchronized
    fun setNotebookDefault(documentId: String, templateId: String?): PageTemplateLibrary {
        val current = load()
        val defaults = current.notebookDefaults.toMutableMap()
        if (templateId == null || current.template(templateId) == null) defaults.remove(documentId) else defaults[documentId] = templateId
        return save(current.copy(notebookDefaults = defaults))
    }

    @Synchronized
    fun delete(templateId: String): PageTemplateLibrary {
        if (PageTemplateCatalog.isBuiltIn(templateId)) return load()
        val current = load()
        return save(current.copy(
            customTemplates = current.customTemplates.filterNot { it.id == templateId },
            favoriteIds = current.favoriteIds - templateId,
            notebookDefaults = current.notebookDefaults.filterValues { it != templateId },
        ))
    }

    private fun sanitize(source: PageTemplateLibrary): PageTemplateLibrary {
        val custom = source.customTemplates.asSequence()
            .filterNot { PageTemplateCatalog.isBuiltIn(it.id) }
            .filter(::isValid)
            .distinctBy(PageTemplate::id)
            .take(MAX_CUSTOM_TEMPLATES)
            .map { it.copy(name = it.name.trim().take(MAX_TEMPLATE_NAME_LENGTH)) }
            .toList()
        val ids = (PageTemplateCatalog.builtIns.map(PageTemplate::id) + custom.map(PageTemplate::id)).toSet()
        return PageTemplateLibrary(
            customTemplates = custom,
            favoriteIds = source.favoriteIds.filterTo(linkedSetOf()) { it in ids },
            notebookDefaults = source.notebookDefaults.entries.asSequence()
                .filter { (documentId, templateId) -> documentId.isNotBlank() && templateId in ids }
                .take(MAX_NOTEBOOK_DEFAULTS)
                .associate { it.toPair() },
        )
    }

    private fun isValid(template: PageTemplate): Boolean = template.id.isNotBlank() &&
        template.name.isNotBlank() && template.width.isFinite() && template.height.isFinite() &&
        template.width in MIN_PAGE_DIMENSION..MAX_PAGE_DIMENSION && template.height in MIN_PAGE_DIMENSION..MAX_PAGE_DIMENSION &&
        when (val background = template.background) {
            is PageBackground.Paper -> true
            is PageBackground.Image -> background.assetPath.isNotBlank()
            is PageBackground.Pdf -> background.assetPath.isNotBlank() && background.pageIndex >= 0
        }
}

private const val DEFAULT_PAGE_WIDTH = 1600f
private const val DEFAULT_PAGE_HEIGHT = 2263f
private const val MIN_PAGE_DIMENSION = 32f
private const val MAX_PAGE_DIMENSION = 20_000f
private const val MAX_CUSTOM_TEMPLATES = 200
private const val MAX_NOTEBOOK_DEFAULTS = 500
private const val MAX_TEMPLATE_NAME_LENGTH = 80
