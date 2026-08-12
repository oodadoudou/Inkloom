package app.inkloom.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.inkloom.engine.v2.brush.EngineV2BrushEvaluator
import app.inkloom.engine.v2.render.EngineV2StampBatchRenderer
import app.inkloom.engine.v2.render.ProceduralBrushTip
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DocumentAssetService(private val context: Context) {
    private val directory = File(context.filesDir, "document-assets").apply { mkdirs() }
    private val brushAssetDirectory = File(context.filesDir, "brush-assets").apply { mkdirs() }
    private val assetLifecycleLock = Any()
    private val pendingDocumentAssets = linkedSetOf<String>()

    fun importPdf(uri: Uri, title: String, indexText: Boolean = true): InkDocument {
        val asset = copyAsset(uri, "pdf")
        val descriptor = ParcelFileDescriptor.open(asset, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val pageIds = List(renderer.pageCount) { UUID.randomUUID().toString() }
        val destinationPages = pageIds.mapIndexed { index, id ->
            renderer.openPage(index).use { page ->
                index to PdfDestinationPage(id, page.width.toFloat(), page.height.toFloat())
            }
        }.toMap()
        val pages = (0 until renderer.pageCount).map { index ->
            renderer.openPage(index).use { page ->
                val textBlocks = if (indexText) extractPdfText(page) else null
                val gotoLinks = if (indexText) extractPdfGotoLinks(page, destinationPages) else null
                InkPage(
                    id = pageIds[index],
                    title = "Page ${index + 1}",
                    width = page.width.toFloat(),
                    height = page.height.toFloat(),
                    background = PageBackground.Pdf(asset.absolutePath, index),
                    pdfTextBlocks = textBlocks.orEmpty(),
                    pdfTextIndexVersion = if (textBlocks != null) PDF_TEXT_INDEX_VERSION else 0,
                    pdfGotoLinks = gotoLinks.orEmpty(),
                    pdfLinkIndexVersion = if (gotoLinks != null) PDF_LINK_INDEX_VERSION else 0,
                )
            }
        }
        renderer.close(); descriptor.close()
        require(pages.isNotEmpty()) { "PDF contains no pages" }
        return InkDocument(title = title.substringBeforeLast('.').ifBlank { "Imported PDF" }, coverArgb = 0xFF707070, pages = pages)
    }

    fun indexPdfText(document: InkDocument): InkDocument {
        if (Build.VERSION.SDK_INT < 35) return document
        val updates = mutableMapOf<String, PdfPageIndexUpdate>()
        document.pages
            .filter { it.background is PageBackground.Pdf && (it.pdfTextIndexVersion < PDF_TEXT_INDEX_VERSION || it.pdfLinkIndexVersion < PDF_LINK_INDEX_VERSION) }
            .groupBy { (it.background as PageBackground.Pdf).assetPath }
            .forEach { (assetPath, pages) ->
                val descriptor = runCatching { ParcelFileDescriptor.open(File(assetPath), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return@forEach
                val renderer = runCatching { PdfRenderer(descriptor) }.getOrElse { descriptor.close(); return@forEach }
                try {
                    val destinationPages = document.pages.filter { page -> (page.background as? PageBackground.Pdf)?.assetPath == assetPath }.associate { page ->
                        val rotation = page.backgroundRotationQuarterTurns.mod(4)
                        val sourceWidth = if (rotation % 2 == 0) page.width else page.height
                        val sourceHeight = if (rotation % 2 == 0) page.height else page.width
                        (page.background as PageBackground.Pdf).pageIndex to PdfDestinationPage(page.id, sourceWidth, sourceHeight, rotation)
                    }
                    pages.forEach pageLoop@{ inkPage ->
                        val pageIndex = (inkPage.background as PageBackground.Pdf).pageIndex
                        if (pageIndex !in 0 until renderer.pageCount) return@pageLoop
                        val indexed = runCatching {
                            renderer.openPage(pageIndex).use { pdfPage ->
                                PdfPageIndexUpdate(
                                    textBlocks = extractPdfText(pdfPage) ?: return@use null,
                                    gotoLinks = extractPdfGotoLinks(pdfPage, destinationPages) ?: return@use null,
                                )
                            }
                        }.getOrNull() ?: return@pageLoop
                        val textBlocks = indexed.textBlocks
                        val gotoLinks = indexed.gotoLinks
                        val rotatedBlocks = textBlocks.map { block ->
                            block.copy(bounds = block.bounds.map { sourceBounds ->
                                generateSequence(sourceBounds, PageGeometry::rotateTextBoundsClockwise)
                                    .drop(inkPage.backgroundRotationQuarterTurns.mod(4))
                                    .first()
                            })
                        }
                        val rotatedLinks = gotoLinks.map { link -> link.copy(bounds = link.bounds.map { sourceBounds ->
                            generateSequence(sourceBounds, PageGeometry::rotateTextBoundsClockwise)
                                .drop(inkPage.backgroundRotationQuarterTurns.mod(4))
                                .first()
                        }) }
                        updates[inkPage.id] = PdfPageIndexUpdate(rotatedBlocks, rotatedLinks)
                    }
                } finally {
                    renderer.close()
                    descriptor.close()
                }
            }
        if (updates.isEmpty()) return document
        return document.copy(
            pages = document.pages.map { page ->
                updates[page.id]?.let { update -> page.copy(pdfTextBlocks = update.textBlocks, pdfTextIndexVersion = PDF_TEXT_INDEX_VERSION, pdfGotoLinks = update.gotoLinks, pdfLinkIndexVersion = PDF_LINK_INDEX_VERSION) } ?: page
            },
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun importImage(uri: Uri, title: String): InkDocument {
        val asset = copyAsset(uri, title.substringAfterLast('.', "image"))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(asset.absolutePath, options)
        val width = options.outWidth.takeIf { it > 0 } ?: 1600
        val height = options.outHeight.takeIf { it > 0 } ?: 2263
        return InkDocument(title = title.substringBeforeLast('.').ifBlank { "Imported Image" }, pages = listOf(InkPage(width = width.toFloat(), height = height.toFloat(), background = PageBackground.Image(asset.absolutePath))))
    }

    /** Copies the selected file into private app storage and exposes every PDF page as a reusable template. */
    fun importPageTemplates(uri: Uri, displayName: String): List<PageTemplate> {
        val isPdf = context.contentResolver.getType(uri) == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
        val source = if (isPdf) importPdf(uri, displayName, indexText = false) else importImage(uri, displayName)
        val baseName = displayName.substringBeforeLast('.').trim().ifBlank { "Imported template" }
        return source.pages.mapIndexed { index, page ->
            PageTemplate(
                name = if (source.pages.size == 1) baseName else "$baseName · ${index + 1}",
                width = page.width,
                height = page.height,
                background = page.background,
            )
        }
    }

    fun importPageImage(uri: Uri): PageElement.Image {
        val asset = copyAsset(uri, "image")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(asset.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Selected image could not be decoded" }
        val width = options.outWidth.toFloat().coerceAtMost(720f).coerceAtLeast(120f)
        val height = (width * options.outHeight / options.outWidth.toFloat()).coerceAtMost(900f).coerceAtLeast(90f)
        return PageElement.Image(x = .18f, y = .2f, width = width, height = height, assetPath = asset.absolutePath)
    }

    /** Persists a paint-bucket fill mask PNG and returns its absolute path. */
    fun saveFillMask(mask: Bitmap): String = saveDocumentPng(mask, "fill-mask")

    /** Persists the full-page premultiplied RGBA surface owned by a raster layer. */
    fun saveRasterLayer(bitmap: Bitmap): String = saveDocumentPng(bitmap, "raster-layer")

    private fun saveDocumentPng(bitmap: Bitmap, kind: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val temporary = File(directory, ".${System.nanoTime()}.tmp")
        try {
            temporary.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 0, out)) { "Could not encode $kind" }
                out.flush()
            }
            temporary.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "$hash.$kind.png")
            synchronized(assetLifecycleLock) {
                if (target.exists()) temporary.delete() else check(temporary.renameTo(target)) { "Could not retain $kind" }
                pendingDocumentAssets += target.canonicalPath
            }
            return target.absolutePath
        } finally {
            temporary.delete()
        }
    }

    fun releaseAssetReservation(path: String?) {
        if (path.isNullOrBlank()) return
        synchronized(assetLifecycleLock) {
            runCatching { File(path).canonicalPath }.getOrNull()?.let(pendingDocumentAssets::remove)
        }
    }

    /** Loads a fill mask PNG, or null if it cannot be decoded. */
    fun loadFillMask(maskAssetPath: String): Bitmap? = runCatching { BitmapFactory.decodeFile(maskAssetPath) }.getOrNull()

    /** Removes only private document assets that are no longer referenced by a document or template. */
    fun garbageCollectUnreferencedAssets(
        documents: List<InkDocument>,
        pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
    ): Int = synchronized(assetLifecycleLock) {
        DocumentAssetGarbageCollector.collect(directory, documents, pageTemplates, pendingDocumentAssets.toSet())
    }

    fun importCoverImage(uri: Uri): String {
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/')?.take(8).orEmpty().ifBlank { "image" }
        val asset = copyAsset(uri, extension)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(asset.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Selected cover could not be decoded" }
        return asset.absolutePath
    }

    fun renderBackground(background: PageBackground, targetWidth: Int, rotationQuarterTurns: Int = 0, crop: PageCrop = PageCrop()): Bitmap? {
        val sourceBitmap = when (background) {
        is PageBackground.Paper -> null
        is PageBackground.Image -> BitmapFactory.decodeFile(background.assetPath)?.let { source ->
            val result = Bitmap.createScaledBitmap(source, targetWidth, (source.height * targetWidth.toFloat() / source.width).toInt().coerceAtLeast(1), true)
            if (result !== source) source.recycle()
            result
        }
        is PageBackground.Pdf -> {
            val descriptor = ParcelFileDescriptor.open(File(background.assetPath), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val page = renderer.openPage(background.pageIndex)
            val height = (page.height * targetWidth.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close(); renderer.close(); descriptor.close(); bitmap
        }
        }
        return cropBackground(rotateQuarterTurns(sourceBitmap, rotationQuarterTurns), crop, targetWidth)
    }

    fun exportPdf(document: InkDocument, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { exportPdf(document, it) } ?: error("Could not open export destination")
    }

    fun exportPdf(document: InkDocument, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            document.pages.forEachIndexed { index, page ->
                val outputWidth = 1200
                val outputHeight = (outputWidth * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(outputWidth, outputHeight, index + 1).create())
                drawPage(pdfPage.canvas, page, outputWidth, outputHeight)
                drawSearchableTextLayer(pdfPage.canvas, page, outputWidth, outputHeight)
                pdf.finishPage(pdfPage)
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun drawSearchableTextLayer(canvas: Canvas, page: InkPage, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 1
            typeface = Typeface.DEFAULT
        }
        val validRecognized = page.recognizedTextBlocks.filter { block ->
            when (block.source) {
                RecognizedTextSource.HANDWRITING -> page.handwritingIndexFingerprint == HandwritingIndex.fingerprint(page.strokes)
                RecognizedTextSource.IMAGE_OCR -> page.imageOcrFingerprint == ImageOcrIndex.fingerprint(page)
            }
        }.map { it.text to it.bounds }
        val indexedPdf = page.pdfTextBlocks.flatMap { block -> block.bounds.map { block.text to it } }
        (validRecognized + indexedPdf).forEach { (rawText, sourceBounds) ->
            val bounds = PageCropTransform.project(sourceBounds, page.crop) ?: return@forEach
            val text = rawText.replace('\u0000', ' ').replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return@forEach
            val left = bounds.left * width
            val baseline = (bounds.bottom * height).coerceIn(1f, height.toFloat())
            paint.textSize = ((bounds.bottom - bounds.top) * height).coerceIn(4f, 96f)
            val availableWidth = ((bounds.right - bounds.left) * width).coerceAtLeast(4f)
            val measuredWidth = paint.measureText(text)
            if (measuredWidth > availableWidth) paint.textSize = (paint.textSize * availableWidth / measuredWidth).coerceAtLeast(2f)
            canvas.drawText(text.take(MAX_PDF_TEXT_CHARACTERS_PER_PAGE), left, baseline, paint)
        }
    }

    fun exportPagePng(page: InkPage, uri: Uri) {
        val width = 1600
        val bitmap = renderPageBitmap(page, width)
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("Could not open image destination")
        bitmap.recycle()
    }

    fun renderPageBitmap(page: InkPage, width: Int): Bitmap {
        require(width in 1..8192) { "Render width must be between 1 and 8192 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(Canvas(bitmap), page, width, height)
        return bitmap
    }

    fun renderElementLayerBitmap(page: InkPage, layerId: String, width: Int, contentIds: Set<String>? = null): Bitmap {
        require(width in 1..4096) { "Layer render width must be between 1 and 4096 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(
            Canvas(bitmap), page, width, height,
            includeBackground = false,
            includedLayerIds = setOf(layerId),
            includedContentIds = contentIds,
            includeStrokes = false,
            includeRaster = false,
            applyLayerEffects = false,
            includeDraftLayers = true,
        )
        return bitmap
    }

    /** Renders only the persistent raster surface for one layer in the page's visible crop.
     * Layer opacity, blend mode, clipping, and masks are deliberately applied by the live canvas
     * so the returned bitmap remains a reusable source surface. */
    fun renderRasterLayerBitmap(page: InkPage, layerId: String, width: Int): Bitmap {
        require(width in 1..4096) { "Layer render width must be between 1 and 4096 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(
            Canvas(bitmap), page, width, height,
            includeBackground = false,
            includedLayerIds = setOf(layerId),
            includeStrokes = false,
            includeRaster = true,
            applyLayerEffects = false,
            includeDraftLayers = true,
        )
        return bitmap
    }

    /** Merges an edited visible-crop surface back into a full page-oriented raster asset.
     * Existing pixels outside the crop are preserved. The returned bitmap uses the page's current
     * orientation, so callers must reset rasterRotationQuarterTurns when storing it. */
    fun composeVisibleRasterLayer(page: InkPage, layerId: String, visibleSurface: Bitmap): Bitmap {
        require(!visibleSurface.isRecycled) { "Visible raster surface has been recycled" }
        val crop = PageCropTransform.normalized(page.crop)
        val fullWidth = (visibleSurface.width / (crop.right - crop.left).coerceAtLeast(.01f)).toInt().coerceIn(visibleSurface.width, 4096)
        val fullPage = renderRasterLayerBitmap(page.copy(crop = PageCrop()), layerId, fullWidth)
        val destination = android.graphics.Rect(
            (crop.left * fullPage.width).toInt().coerceIn(0, fullPage.width - 1),
            (crop.top * fullPage.height).toInt().coerceIn(0, fullPage.height - 1),
            (crop.right * fullPage.width).toInt().coerceIn(1, fullPage.width),
            (crop.bottom * fullPage.height).toInt().coerceIn(1, fullPage.height),
        )
        if (destination.right > destination.left && destination.bottom > destination.top) {
            Canvas(fullPage).drawBitmap(visibleSurface, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return fullPage
    }

    fun exportBundle(document: InkDocument, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { exportBundle(document, it) }
            ?: error("Could not open package destination")
    }

    fun exportBundle(document: InkDocument, output: OutputStream) {
        val serializer = Json { prettyPrint = true; classDiscriminator = "type" }
        ZipOutputStream(output.buffered()).use { zip ->
            val portable = document.copy(
                coverImagePath = document.coverImagePath?.let { "assets/${File(it).name}" },
                pages = document.pages.map { page -> page.copy(
                background = when (val background = page.background) {
                    is PageBackground.Pdf -> background.copy(assetPath = "assets/${File(background.assetPath).name}")
                    is PageBackground.Image -> background.copy(assetPath = "assets/${File(background.assetPath).name}")
                    is PageBackground.Paper -> background
                },
                layers = page.layers.map { layer -> layer.copy(
                    rasterAssetPath = layer.rasterAssetPath?.let { "assets/${File(it).name}" },
                ) },
                strokes = page.strokes.map { it.portableBrushAssets() },
                elements = page.elements.map { element -> when (element) {
                    is PageElement.Image -> element.copy(assetPath = "assets/${File(element.assetPath).name}")
                    is PageElement.AudioMarker -> element.copy(assetPath = "assets/${File(element.assetPath).name}")
                    is PageElement.Fill -> element.copy(maskAssetPath = "assets/${File(element.maskAssetPath).name}")
                    is PageElement.Shape -> element.copy(maskAssetPath = element.maskAssetPath?.let { "assets/${File(it).name}" })
                    is PageElement.Sticky -> element.copy(strokes = element.strokes.map { it.portableBrushAssets() })
                    else -> element
                } },
                ) },
            )
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(serializer.encodeToString(portable).encodeToByteArray()); zip.closeEntry()
            val assets = listOfNotNull(document.coverImagePath) +
                document.pages.mapNotNull { page -> when (val background = page.background) { is PageBackground.Pdf -> background.assetPath; is PageBackground.Image -> background.assetPath; else -> null } } +
                document.pages.flatMap(InkPage::layers).mapNotNull(InkLayer::rasterAssetPath) +
                document.pages.flatMap { it.elements }.mapNotNull { element -> when (element) { is PageElement.Image -> element.assetPath; is PageElement.AudioMarker -> element.assetPath; is PageElement.Fill -> element.maskAssetPath; is PageElement.Shape -> element.maskAssetPath; else -> null } }
            writeDistinctFiles(zip, "assets", assets)
            val allStrokes = document.pages.flatMap { page -> page.strokes + page.elements.filterIsInstance<PageElement.Sticky>().flatMap(PageElement.Sticky::strokes) }
            writeDistinctFiles(zip, "brush-assets", allStrokes.flatMap { it.brushAssetPaths() })
            File(context.filesDir, "brush-sources").listFiles()?.filter(File::isFile)?.forEach { file -> zip.putNextEntry(ZipEntry("brush-sources/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }

    fun importBundle(uri: Uri): InkDocument {
        return context.contentResolver.openInputStream(uri)?.use(::importBundle)
            ?: error("Selected bundle is unreadable")
    }

    fun importBundle(input: InputStream): InkDocument {
        val temporaryRoot = File(context.cacheDir, "bundle-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var entries = 0
            var totalBytes = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += 1
                    require(entries <= 10_000) { "Bundle contains too many entries" }
                    val normalized = entry.name.replace('\\', '/')
                    require(normalized.isNotBlank() && !normalized.startsWith('/') && normalized.split('/').none { it == ".." }) { "Bundle contains an unsafe path" }
                    require(normalized == "manifest.json" || normalized.startsWith("assets/") || normalized.startsWith("brush-assets/") || normalized.startsWith("brush-sources/")) { "Bundle contains an unsupported entry" }
                    if (!entry.isDirectory) {
                        val target = File(temporaryRoot, normalized)
                        require(target.canonicalPath.startsWith(temporaryRoot.canonicalPath + File.separator)) { "Bundle path escapes its container" }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= 512L * 1024 * 1024) { "Bundle expands beyond the 512 MB safety limit" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = File(temporaryRoot, "manifest.json")
            require(manifest.isFile) { "Bundle has no manifest.json" }
            val serializer = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
            val decoded = serializer.decodeFromString<InkDocument>(manifest.readText())
            fun retained(relative: String, brush: Boolean = false): String {
                val normalized = relative.replace('\\', '/')
                val expectedPrefix = if (brush) "brush-assets/" else "assets/"
                require(normalized.startsWith(expectedPrefix) && !normalized.startsWith('/') && normalized.split('/').none { it == ".." }) { "Manifest contains an unsafe asset path" }
                val source = File(temporaryRoot, normalized)
                require(source.canonicalPath.startsWith(temporaryRoot.canonicalPath + File.separator) && source.isFile) { "Bundle is missing $normalized" }
                return retainFile(source, if (brush) brushAssetDirectory else directory).absolutePath
            }
            return decoded.copy(
                id = UUID.randomUUID().toString(),
                coverImagePath = decoded.coverImagePath?.let(::retained),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                trashedAt = null,
                archivedAt = null,
                pages = decoded.pages.map { page -> page.copy(
                    background = when (val background = page.background) {
                        is PageBackground.Pdf -> background.copy(assetPath = retained(background.assetPath))
                        is PageBackground.Image -> background.copy(assetPath = retained(background.assetPath))
                        is PageBackground.Paper -> background
                    },
                    layers = page.layers.map { layer -> layer.copy(
                        rasterAssetPath = layer.rasterAssetPath?.let { retained(it) },
                    ) },
                    strokes = page.strokes.map { it.withRetainedBrushAssets(::retained) },
                    elements = page.elements.map { element -> when (element) {
                        is PageElement.Image -> element.copy(assetPath = retained(element.assetPath))
                        is PageElement.AudioMarker -> element.copy(assetPath = retained(element.assetPath))
                        is PageElement.Fill -> element.copy(maskAssetPath = retained(element.maskAssetPath))
                        is PageElement.Shape -> element.copy(maskAssetPath = element.maskAssetPath?.let { retained(it) })
                        is PageElement.Sticky -> element.copy(strokes = element.strokes.map { it.withRetainedBrushAssets(::retained) })
                        else -> element
                    } },
                ) },
            )
        } finally {
            temporaryRoot.deleteRecursively()
        }
    }

    private fun writeDistinctFiles(zip: ZipOutputStream, folder: String, paths: List<String>) {
        paths.distinct().map(::File).filter(File::isFile).distinctBy { it.name }.forEach { file ->
            zip.putNextEntry(ZipEntry("$folder/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
    }

    private fun extractPdfText(page: PdfRenderer.Page): List<PdfTextBlock>? {
        if (Build.VERSION.SDK_INT < 35) return null
        var remainingCharacters = MAX_PDF_TEXT_CHARACTERS_PER_PAGE
        var boundsLookups = 0
        return runCatching {
            buildList {
                page.textContents.take(MAX_PDF_TEXT_BLOCKS_PER_PAGE).forEach { content ->
                    if (remainingCharacters <= 0) return@forEach
                    val normalized = content.text.replace('\u0000', ' ').replace(Regex("\\s+"), " ").trim()
                    if (normalized.isEmpty()) return@forEach
                    val text = normalized.take(remainingCharacters)
                    remainingCharacters -= text.length
                    val sourceBounds = content.bounds.ifEmpty {
                        if (boundsLookups >= MAX_PDF_BOUNDS_LOOKUPS_PER_PAGE) emptyList()
                        else {
                            boundsLookups += 1
                            val needle = text.take(MAX_PDF_SEARCH_NEEDLE_CHARACTERS).trim()
                            if (needle.isEmpty()) emptyList() else page.searchText(needle).firstOrNull()?.bounds.orEmpty()
                        }
                    }
                    val bounds = sourceBounds.take(MAX_PDF_BOUNDS_PER_BLOCK).mapNotNull { rect ->
                        val left = (rect.left / page.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val top = (rect.top / page.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val right = (rect.right / page.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val bottom = (rect.bottom / page.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        PdfTextBounds(left, top, right, bottom).takeIf {
                            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() && right > left && bottom > top
                        }
                    }
                    add(PdfTextBlock(text, bounds))
                }
            }
        }.getOrNull()
    }

    private fun extractPdfGotoLinks(page: PdfRenderer.Page, destinationPages: Map<Int, PdfDestinationPage>): List<PdfGotoLink>? {
        if (Build.VERSION.SDK_INT < 35) return null
        return runCatching {
            page.gotoLinks.take(MAX_PDF_GOTO_LINKS_PER_PAGE).mapNotNull { source ->
                val destination = source.destination
                val destinationPage = destinationPages[destination.pageNumber] ?: return@mapNotNull null
                val bounds = source.bounds.take(MAX_PDF_BOUNDS_PER_LINK).mapNotNull { rect ->
                    val left = (rect.left / page.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val top = (rect.top / page.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val right = (rect.right / page.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val bottom = (rect.bottom / page.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    PdfTextBounds(left, top, right, bottom).takeIf { left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() && right > left && bottom > top }
                }
                if (bounds.isEmpty()) return@mapNotNull null
                val targetPage = destinationPages[destination.pageNumber] ?: return@mapNotNull null
                var link = PdfGotoLink(
                    bounds = bounds,
                    destinationPageId = destinationPage.id,
                    destinationX = (destination.xCoordinate / targetPage.width.coerceAtLeast(1f)).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
                    destinationY = (destination.yCoordinate / targetPage.height.coerceAtLeast(1f)).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
                    destinationZoom = destination.zoom.takeIf(Float::isFinite)?.coerceIn(0f, 8f) ?: 0f,
                )
                repeat(destinationPage.rotationQuarterTurns.mod(4)) { link = PdfGotoLinkNavigation.rotateDestinationClockwise(link) }
                link
            }
        }.getOrNull()
    }

    private fun InkStroke.portableBrushAssets() = copy(
        masks = masks.map { mask -> mask.copy(tipAssetPath = mask.tipAssetPath?.let { "brush-assets/${File(it).name}" }) },
        tipAssetPath = tipAssetPath?.let { "brush-assets/${File(it).name}" },
        tipAssetPaths = tipAssetPaths.map { "brush-assets/${File(it).name}" },
        dualTipAssetPaths = dualTipAssetPaths.map { "brush-assets/${File(it).name}" },
        grainAssetPath = grainAssetPath?.let { "brush-assets/${File(it).name}" },
    )

    private fun InkStroke.withRetainedBrushAssets(retained: (String, Boolean) -> String) = copy(
        masks = masks.map { mask -> mask.copy(tipAssetPath = mask.tipAssetPath?.let { retained(it, true) }) },
        tipAssetPath = tipAssetPath?.let { retained(it, true) },
        tipAssetPaths = tipAssetPaths.map { retained(it, true) },
        dualTipAssetPaths = dualTipAssetPaths.map { retained(it, true) },
        grainAssetPath = grainAssetPath?.let { retained(it, true) },
    )

    private fun InkStroke.brushAssetPaths(): List<String> =
        tipAssetPaths + dualTipAssetPaths + masks.mapNotNull(StrokeMaskDab::tipAssetPath) + listOfNotNull(tipAssetPath, grainAssetPath)

    private fun retainFile(source: File, targetDirectory: File): File {
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input -> val buffer = ByteArray(DEFAULT_BUFFER_SIZE); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val extension = source.extension.lowercase().filter(Char::isLetterOrDigit).take(12)
        val target = File(targetDirectory, hash + extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty())
        if (!target.exists()) source.copyTo(target)
        return target
    }

    private fun drawPage(
        canvas: Canvas,
        sourcePage: InkPage,
        width: Int,
        height: Int,
        includeBackground: Boolean = true,
        includedLayerIds: Set<String>? = null,
        includedContentIds: Set<String>? = null,
        includeStrokes: Boolean = true,
        includeRaster: Boolean = true,
        applyLayerEffects: Boolean = true,
        includeDraftLayers: Boolean = false,
    ) {
        val page = PageCropTransform.project(sourcePage)
        val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
        val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.toInt() ?: Color.WHITE
        if (includeBackground) {
            canvas.drawColor(if (backgroundVisible) paperColor else Color.WHITE)
            if (backgroundVisible) renderBackground(sourcePage.background, width, sourcePage.backgroundRotationQuarterTurns, sourcePage.crop)?.let { bitmap -> canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), null); bitmap.recycle() }
            if (backgroundVisible) (page.background as? PageBackground.Paper)?.let { drawPaperPattern(canvas, it.pattern, width, height) }
        } else canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val visibleLayers = page.layers.filter { LayerHierarchy.effectiveVisible(page.layers, it) && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) && (includeDraftLayers || !it.draft) && (includedLayerIds == null || it.id in includedLayerIds) }
        visibleLayers.forEachIndexed { layerIndex, layer ->
            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                blendMode = layer.blendMode.toBlendMode()
            }
            val layerCheckpoint = if (applyLayerEffects) canvas.saveLayer(null, layerPaint) else canvas.save()
            fun contentAllowed(id: String, contentIds: Set<String>?): Boolean =
                (includedContentIds == null || id in includedContentIds) && (contentIds == null || id in contentIds)
            fun drawLayerItems(targetLayerId: String, contentIds: Set<String>? = null) {
            if (includeStrokes) drawStrokes(canvas, page.strokes.filter { it.layerId == targetLayerId && contentAllowed(it.id, contentIds) }, width, height, paint)
            page.elements.filter { it.layerId == targetLayerId && contentAllowed(it.id, contentIds) }.forEach { element ->
            val left = element.x * width
            val top = element.y * height
            val right = left + element.width / page.width * width
            val bottom = top + element.height / page.height * height
            canvas.save()
            canvas.rotate(element.rotationDegrees, (left + right) / 2f, (top + bottom) / 2f)
            paint.alpha = (element.opacity.coerceIn(0f, 1f) * 255).toInt()
            when (element) {
                is PageElement.Text -> {
                    paint.style = Paint.Style.FILL; paint.color = element.colorArgb.toInt(); paint.textSize = element.textSize * width / page.width
                    paint.typeface = Typeface.create(Typeface.DEFAULT, when { element.bold && element.italic -> Typeface.BOLD_ITALIC; element.bold -> Typeface.BOLD; element.italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
                    paint.isUnderlineText = element.underline
                    paint.textAlign = when (element.alignment) { InkTextAlignment.START -> Paint.Align.LEFT; InkTextAlignment.CENTER -> Paint.Align.CENTER; InkTextAlignment.END -> Paint.Align.RIGHT }
                    val textX = when (element.alignment) { InkTextAlignment.START -> left; InkTextAlignment.CENTER -> (left + right) / 2f; InkTextAlignment.END -> right }
                    val lineHeight = paint.textSize * element.lineSpacing.coerceIn(.8f, 2f)
                    val maxLines = ((bottom - top) / lineHeight).toInt().coerceAtLeast(1)
                    RichTextFormatter.lines(element.text, element.listStyle).take(maxLines).forEachIndexed { index, line -> canvas.drawText(line, textX, top + paint.textSize + index * lineHeight, paint) }
                    paint.typeface = Typeface.DEFAULT; paint.isUnderlineText = false; paint.textAlign = Paint.Align.LEFT
                }
                is PageElement.Sticky -> {
                    paint.style = Paint.Style.FILL
                    paint.color = element.colorArgb.toInt()
                    canvas.drawRect(left, top, right, bottom, paint)
                    paint.color = Color.DKGRAY
                    paint.textSize = 28f
                    element.text.lineSequence().take(if (element.collapsed) 1 else 8).forEachIndexed { index, line ->
                        canvas.drawText(line, left + 14, top + 38 + index * 34, paint)
                    }
                    if (!element.collapsed && element.strokes.isNotEmpty()) {
                        val stickyWidth = (right - left).toInt().coerceAtLeast(1)
                        val stickyHeight = (bottom - top).toInt().coerceAtLeast(1)
                        canvas.save()
                        canvas.clipRect(left, top, right, bottom)
                        canvas.translate(left, top)
                        drawStrokes(canvas, element.strokes, stickyWidth, stickyHeight, paint)
                        canvas.restore()
                    }
                }
                is PageElement.Shape -> {
                    val triangle = Path().apply { moveTo((left + right) / 2f, top); lineTo(right, bottom); lineTo(left, bottom); close() }
                    val diamond = Path().apply { moveTo((left + right) / 2f, top); lineTo(right, (top + bottom) / 2f); lineTo((left + right) / 2f, bottom); lineTo(left, (top + bottom) / 2f); close() }
                    val freeform = Path().apply {
                        element.path.firstOrNull()?.let { first ->
                            moveTo(left + first.x * (right - left), top + first.y * (bottom - top))
                            for (index in 1 until element.path.size) { val vertex = element.path[index]; lineTo(left + vertex.x * (right - left), top + vertex.y * (bottom - top)) }
                            close()
                        }
                    }
                    element.fillArgb?.let { fill ->
                        paint.style = Paint.Style.FILL
                        paint.color = fill.toInt()
                        val gradientDefinition = element.gradientDefinition()
                        val maskCheckpoint = if (gradientDefinition != null) {
                            element.maskAssetPath?.let { path ->
                                loadFillMask(path)?.let { mask ->
                                    val checkpoint = canvas.saveLayer(left, top, right, bottom, null)
                                    canvas.drawBitmap(mask, null, android.graphics.RectF(left, top, right, bottom), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                                    mask.recycle()
                                    canvas.saveLayer(left, top, right, bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        blendMode = android.graphics.BlendMode.SRC_IN
                                    })
                                    checkpoint
                                }
                            }
                        } else null
                        if (element.kind == ShapeKind.RECTANGLE && gradientDefinition != null) {
                            paint.isDither = gradientDefinition.dither
                            paint.shader = GradientShaderFactory.create(
                                gradientDefinition,
                                left + element.gradientStartX * (right - left),
                                top + element.gradientStartY * (bottom - top),
                                left + element.gradientEndX * (right - left),
                                top + element.gradientEndY * (bottom - top),
                            )
                            canvas.drawRect(left, top, right, bottom, paint)
                            paint.shader = null
                            paint.isDither = true
                        } else when (element.kind) { ShapeKind.ELLIPSE -> canvas.drawOval(left, top, right, bottom, paint); ShapeKind.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint); ShapeKind.TRIANGLE -> canvas.drawPath(triangle, paint); ShapeKind.DIAMOND -> canvas.drawPath(diamond, paint); ShapeKind.FREEFORM -> canvas.drawPath(freeform, paint); else -> Unit }
                        if (maskCheckpoint != null) canvas.restoreToCount(maskCheckpoint)
                    }
                    paint.style = Paint.Style.STROKE; paint.color = element.colorArgb.toInt(); paint.strokeWidth = element.strokeWidth
                    if (element.strokeWidth > 0f) when (element.kind) {
                        ShapeKind.ELLIPSE -> canvas.drawOval(left, top, right, bottom, paint)
                        ShapeKind.TRIANGLE -> canvas.drawPath(triangle, paint)
                        ShapeKind.DIAMOND -> canvas.drawPath(diamond, paint)
                        ShapeKind.LINE -> canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, paint)
                        ShapeKind.ARROW -> { val y = (top + bottom) / 2f; canvas.drawLine(left, y, right, y, paint); canvas.drawLine(right, y, right - 28, y - 18, paint); canvas.drawLine(right, y, right - 28, y + 18, paint) }
                        ShapeKind.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint)
                        ShapeKind.FREEFORM -> canvas.drawPath(freeform, paint)
                    }
                }
                is PageElement.Image -> ImageAssetDecoder.decodeCropped(element.assetPath, element.crop)?.let { bitmap ->
                    val destination = android.graphics.RectF(left, top, right, bottom)
                    canvas.save()
                    when (element.mask) {
                        ImageMask.RECTANGLE -> Unit
                        ImageMask.ROUNDED -> {
                            val radius = element.cornerRadius * width / page.width
                            canvas.clipPath(Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) })
                        }
                        ImageMask.OVAL -> canvas.clipPath(Path().apply { addOval(destination, Path.Direction.CW) })
                    }
                    canvas.drawBitmap(bitmap, null, destination, paint); canvas.restore(); bitmap.recycle()
                }
                is PageElement.Fill -> loadFillMask(element.maskAssetPath)?.let { mask ->
                    val destination = android.graphics.RectF(left, top, right, bottom)
                    val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    colorPaint.colorFilter = PorterDuffColorFilter(element.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
                    colorPaint.alpha = (element.opacity.coerceIn(0f, 1f) * 255).toInt()
                    canvas.drawBitmap(mask, null, destination, colorPaint)
                    mask.recycle()
                }
                is PageElement.AudioMarker -> { paint.style = Paint.Style.FILL; paint.color = 0xFFE7E7E7.toInt(); canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, paint); paint.color = 0xFF111111.toInt(); paint.textSize = 28f; canvas.drawText("Audio · ${formatDuration(element.durationMs)}", left + 20, top + 42, paint); paint.textSize = 22f; canvas.drawText(element.title, left + 20, top + 76, paint) }
                is PageElement.Link -> {
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFFE7E7E7.toInt()
                    canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, paint)
                    paint.color = 0xFF111111.toInt()
                    paint.textSize = 18f
                    canvas.drawText("LINK ↗", left + 20, top + 30, paint)
                    paint.textSize = 28f
                    canvas.drawText(element.title.take(72), left + 20, top + 66, paint)
                    paint.textSize = 20f
                    canvas.drawText(LinkTarget.displayDestination(element.url, 92), left + 20, top + 98, paint)
                }
            }
            canvas.restore()
            }
            }
            fun drawRasterSurface(targetLayer: InkLayer) {
                if (!includeRaster || targetLayer.contentKind != LayerContentKind.RASTER) return
                val path = targetLayer.rasterAssetPath?.takeIf(String::isNotBlank) ?: return
                BitmapFactory.decodeFile(path)?.let { decoded ->
                    val bitmap = rotateQuarterTurns(decoded, targetLayer.rasterRotationQuarterTurns) ?: return@let
                    val crop = sourcePage.crop
                    val source = android.graphics.Rect(
                        (crop.left.coerceIn(0f, 1f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
                        (crop.top.coerceIn(0f, 1f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1),
                        (crop.right.coerceIn(0f, 1f) * bitmap.width).toInt().coerceIn(1, bitmap.width),
                        (crop.bottom.coerceIn(0f, 1f) * bitmap.height).toInt().coerceIn(1, bitmap.height),
                    )
                    if (source.right > source.left && source.bottom > source.top) {
                        canvas.drawBitmap(bitmap, source, android.graphics.Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                    bitmap.recycle()
                }
            }
            fun drawLayerWithAlphaLock(targetLayer: InkLayer) {
                val capturedIds = targetLayer.alphaLockBaseContentIds.toSet()
                if (!applyLayerEffects || !targetLayer.alphaLock || capturedIds.isEmpty()) {
                    drawRasterSurface(targetLayer)
                    drawLayerItems(targetLayer.id)
                    return
                }
                drawRasterSurface(targetLayer)
                drawLayerItems(targetLayer.id, capturedIds)
                val currentIds = page.strokes.filter { it.layerId == targetLayer.id }.mapTo(mutableSetOf(), InkStroke::id) +
                    page.elements.filter { it.layerId == targetLayer.id }.mapTo(mutableSetOf(), PageElement::id)
                val addedIds = currentIds - capturedIds
                if (addedIds.isNotEmpty()) {
                    val additionsCheckpoint = canvas.saveLayer(null, null)
                    drawLayerItems(targetLayer.id, addedIds)
                    val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    drawRasterSurface(targetLayer)
                    drawLayerItems(targetLayer.id, capturedIds)
                    canvas.restoreToCount(maskCheckpoint)
                    canvas.restoreToCount(additionsCheckpoint)
                }
            }
            drawLayerWithAlphaLock(layer)
            if (applyLayerEffects) layer.mask?.let { mask ->
                val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    blendMode = android.graphics.BlendMode.DST_IN
                })
                LayerMaskRenderer.draw(canvas, mask, width, height)
                canvas.restoreToCount(maskCheckpoint)
            }
            if (applyLayerEffects && layer.clippedToBelow) {
                val lowerLayer = visibleLayers.getOrNull(layerIndex - 1)
                if (lowerLayer == null) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                } else {
                    val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    drawLayerWithAlphaLock(lowerLayer)
                    canvas.restoreToCount(maskCheckpoint)
                }
            }
            canvas.restoreToCount(layerCheckpoint)
        }
    }

    private fun drawPaperPattern(canvas: Canvas, pattern: PaperPattern, width: Int, height: Int) {
        if (pattern == PaperPattern.PLAIN) return
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(227, 227, 227); strokeWidth = 1.5f; style = Paint.Style.STROKE }
        when (pattern) {
            PaperPattern.PLAIN -> Unit
            PaperPattern.RULED -> {
                var y = 36f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, linePaint); y += 36f }
            }
            PaperPattern.GRID -> {
                var x = 0f
                while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), linePaint); x += 32f }
                var y = 0f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, linePaint); y += 32f }
            }
            PaperPattern.DOT -> {
                linePaint.style = Paint.Style.FILL
                linePaint.color = Color.rgb(217, 217, 217)
                var x = 8f
                while (x < width) { var y = 8f; while (y < height) { canvas.drawCircle(x, y, 1.4f, linePaint); y += 28f }; x += 28f }
            }
            PaperPattern.CORNELL -> {
                val cueX = width * .28f
                val summaryY = height * .78f
                linePaint.color = Color.rgb(212, 212, 212)
                canvas.drawLine(cueX, 0f, cueX, summaryY, linePaint)
                canvas.drawLine(0f, summaryY, width.toFloat(), summaryY, linePaint)
                linePaint.color = Color.rgb(231, 231, 231)
                var y = 36f
                while (y < summaryY) { canvas.drawLine(cueX, y, width.toFloat(), y, linePaint); y += 36f }
                y = summaryY + 36f
                while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, linePaint); y += 36f }
            }
        }
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<InkStroke>, width: Int, height: Int, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.isAntiAlias = CspAntiAlias.enabled(stroke.antiAliasMode)
            paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
            val opacityLayer = if (stroke.opacity < .999f) canvas.saveLayer(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt() },
            ) else -1
            val maskLayer = if (stroke.masks.isNotEmpty()) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
            paint.color = stroke.colorArgb.toInt()
            val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
            val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
            val decodedTips = tipPaths.mapNotNull { decodeTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffset.x, grainOffset.y, stroke.grainCompositeMode, stroke.antiAliasMode) }
            val tips = decodedTips.ifEmpty { listOfNotNull(ProceduralBrushTip.create(stroke)) }
            if (tips.isNotEmpty()) {
                val decodedDualTips = if (stroke.dualBrush) stroke.dualTipAssetPaths.mapNotNull { decodeTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) } else emptyList()
                val fallbackDualTips = if (stroke.dualBrush && decodedDualTips.isEmpty()) tipPaths.mapNotNull { decodeTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) } else emptyList()
                val dualTips = decodedDualTips.ifEmpty { fallbackDualTips }
                drawStampStroke(canvas, stroke, tips, dualTips, tipPaths, grainOffset.x, grainOffset.y, width, height, paint)
                decodedDualTips.forEach(Bitmap::recycle)
                fallbackDualTips.forEach(Bitmap::recycle)
                tips.forEach(Bitmap::recycle)
            }
            else {
                val taper = StrokeTaper.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
                val falloff = StrokeFalloff.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.width * width / 1600f, stroke.strokeFalloff)
                if (stroke.points.size == 1) {
                    val point = stroke.points.first()
                    val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point)
                    val strokeWidth = stroke.width * width / 1600f * (taper.firstOrNull() ?: 1f) * dynamics.size
                    val alpha = (stroke.flow * dynamics.depositedAlpha * (falloff?.firstOrNull() ?: 1f) * 255).toInt().coerceIn(0, 255)
                    drawLinePoint(canvas, paint, stroke, point, point.x * width, point.y * height, strokeWidth, alpha, width)
                } else stroke.points.zipWithNext().forEachIndexed { pointIndex, (start, end) ->
                    val point = midpoint(start, end)
                    val taperFactor = (taper[pointIndex] + taper[pointIndex + 1]) / 2f
                    val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point)
                    val strokeWidth = stroke.width * width / 1600f * taperFactor * dynamics.size
                    val falloffFactor = falloff?.let { (it[pointIndex] + it[pointIndex + 1]) / 2f } ?: 1f
                    val alpha = (stroke.flow * dynamics.depositedAlpha * falloffFactor * 255).toInt().coerceIn(0, 255)
                    drawLineSegment(canvas, paint, stroke, point, start.x * width, start.y * height, end.x * width, end.y * height, strokeWidth, alpha, width)
                }
            }
            if (maskLayer >= 0) {
                StrokeMaskRenderer.draw(canvas, paint, stroke.masks, width, height)
                canvas.restoreToCount(maskLayer)
            }
            if (opacityLayer >= 0) canvas.restoreToCount(opacityLayer)
        }
        paint.colorFilter = null
        paint.maskFilter = null
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
    }

    private fun drawStampStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, dualTips: List<Bitmap>, tipPaths: List<String>, grainOffsetX: Float, grainOffsetY: Float, width: Int, height: Int, paint: Paint) {
        val isolateStroke = stroke.blendTipsWithDarken || stroke.dualBrush && dualTips.isNotEmpty()
        val compositeLayer = if (isolateStroke) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.blendMode = if (stroke.blendTipsWithDarken) BlendMode.DARKEN else null
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong())
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor 0x49A2B31DL)
        val dynamicGrainTips = mutableMapOf<String, Bitmap>()
        paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
        val sampledPoints = EngineV2BrushEvaluator.sampleStampPoints(stroke, width.toFloat(), height.toFloat(), dual = false)
        val falloff = StrokeFalloff.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.width * width / 1600f, stroke.strokeFalloff)
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        if (tips.size == 1 && EngineV2StampBatchRenderer.draw(canvas, paint, stroke, tips.single(), sampledPoints, 0, width.toFloat(), height.toFloat())) {
            paint.blendMode = null
            if (compositeLayer >= 0) canvas.restoreToCount(compositeLayer)
            paint.colorFilter = null
            return
        }
        sampledPoints.forEachIndexed { index, point ->
            val stampsAtPoint = BrushStampCount.resolve(stroke.stampCount, stroke.stampCountJitter, stroke.renderSeed, index)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, stroke.ribbon) ?: return@stamp
            val x = point.x * width; val y = point.y * height
            val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point)
            val brushDiameter = (stroke.width * width / 1600f * taper[index] * dynamics.size).coerceAtLeast(.25f)
            val diameter = if (stroke.spray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.particleSize(stroke, point), stroke.sprayParticleSizeUnit, stroke.sprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.spray) SprayGeometry.offset(brushDiameter / 2f * stroke.spraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.sprayBias) else SprayOffset(0f, 0f)
            val drawX = x + offset.x; val drawY = y + offset.y
            val spatialGrain = stroke.grainAssetPath != null && (GrainBehavior.requiresPerDabSampling(stroke.grainMovement, stroke.grainZoom) || stroke.tiltSizeCompression)
            val grainSample = if (spatialGrain) GrainBehavior.sample(
                GrainOffset(grainOffsetX, grainOffsetY), stroke.grainMovement, if (stroke.tiltSizeCompression) 1f else stroke.grainZoom,
                stroke.grainScale, drawX, drawY, if (stroke.tiltSizeCompression) stroke.width * width / 1600f else diameter,
            ) else null
            val tip = if (stroke.grainAssetPath != null && (GrainDynamics.isDynamic(stroke) || spatialGrain)) {
                val densityBucket = (GrainDynamics.density(stroke, point) * 20f).toInt().coerceIn(0, 20)
                val sampleScale = grainSample?.scalePercent ?: stroke.grainScale
                val sampleOffsetX = grainSample?.offset?.x ?: grainOffsetX
                val sampleOffsetY = grainSample?.offset?.y ?: grainOffsetY
                val cacheKey = "$tipIndex:$densityBucket:${sampleScale.toInt()}:${sampleOffsetX.toBits()}:${sampleOffsetY.toBits()}"
                dynamicGrainTips[cacheKey] ?: if (dynamicGrainTips.size < MAX_DYNAMIC_GRAIN_VARIANTS_PER_STROKE) {
                    tipPaths.getOrNull(tipIndex)?.let { path ->
                        decodeTip(path, stroke.hardness, stroke.grainAssetPath, densityBucket / 20f, sampleScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, sampleOffsetX, sampleOffsetY, stroke.grainCompositeMode, stroke.antiAliasMode)
                    }?.also { dynamicGrainTips[cacheKey] = it } ?: tips[tipIndex]
                } else tips[tipIndex]
            } else tips[tipIndex]
            val thicknessDynamic = dynamics.thickness
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = false)
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = false,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val thicknessScale = stroke.thickness.coerceIn(.05f, 2f) * thicknessDynamic
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, thicknessScale, stroke.thicknessVertical)
            val flip = BrushTipFlip.signs(stroke.tipFlipHorizontalMode, stroke.tipFlipVerticalMode, stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed)
            val scaleX = tipScale.x * flip.x
            val scaleY = tipScale.y * flip.y
            val matrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(scaleX, scaleY); postRotate(angle); postTranslate(drawX, drawY) }
            val mainAlpha = (stroke.flow * dynamics.depositedAlpha * (falloff?.get(index) ?: 1f)).coerceIn(0f, 1f).times(255).toInt()
            val jitteredColor = BrushColorJitter.argb(stroke, point, sequenceIndex)
            val tiltBleedAmount = stroke.tiltBleed.coerceIn(0f, 1f) * BrushTilt.response(point, stroke.tiltBleedStart)
            if ((stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) || tiltBleedAmount > .001f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat()) + diameter * tiltBleedAmount * .18f
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical)
                val edgeMatrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x * flip.x, edgeTipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
                paint.colorFilter = PorterDuffColorFilter(WaterEdge.darkenArgb(jitteredColor, stroke.waterEdgeDarkness), PorterDuff.Mode.SRC_IN)
                paint.alpha = (mainAlpha * maxOf(stroke.waterEdgeOpacity.coerceIn(0f, 1f), tiltBleedAmount * .55f)).toInt().coerceIn(1, 255)
                val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat()) + diameter * tiltBleedAmount * .08f
                paint.maskFilter = BrushBlur.quantizedRadius(blur).takeIf { it > 0f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
                canvas.drawBitmap(tip, edgeMatrix, paint)
                paint.maskFilter = null
            }
            paint.colorFilter = PorterDuffColorFilter(jitteredColor, PorterDuff.Mode.SRC_IN)
            paint.alpha = mainAlpha
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * width / 1600f, width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        dynamicGrainTips.values.distinct().filter { dynamic -> tips.none { it === dynamic } }.forEach(Bitmap::recycle)
        paint.blendMode = null
        if (stroke.dualBrush && dualTips.isNotEmpty()) {
            paint.isFilterBitmap = CspAntiAlias.enabled(stroke.dualAntiAliasMode)
            drawDualStampStroke(canvas, stroke, dualTips, width, height, paint)
        }
        if (compositeLayer >= 0) canvas.restoreToCount(compositeLayer)
        paint.colorFilter = null
    }

    /** Export counterpart of InkCanvasView's secondary stamp pass. Keep both algorithms
     * deterministic and equivalent so the page bitmap/PDF matches the live canvas. */
    private fun drawDualStampStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, width: Int, height: Int, paint: Paint) {
        val compositePaint = Paint().apply { blendMode = CspDualBrushBlendMode.resolve(stroke.dualCompositeMode) }
        val dualLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), compositePaint)
        paint.blendMode = if (stroke.dualBlendTipsWithDarken) BlendMode.DARKEN else null
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_SCATTER_SEED)
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_ROTATION_SEED)
        val sampledPoints = EngineV2BrushEvaluator.sampleStampPoints(stroke, width.toFloat(), height.toFloat(), dual = true)
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        val falloff = StrokeFalloff.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.width * width / 1600f, stroke.strokeFalloff)
        sampledPoints.forEachIndexed { index, point ->
            val stampsAtPoint = BrushStampCount.resolve(stroke.dualStampCount, stroke.dualStampCountJitter, stroke.renderSeed, index, dual = true)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed xor DUAL_SEQUENCE_SEED, stroke.dualRibbon) ?: return@stamp
            val tip = tips[tipIndex]
            val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point, dual = true)
            val brushDiameter = (stroke.width * width / 1600f * stroke.dualSizeScale.coerceIn(.01f, 8f) * taper[index] * dynamics.size).coerceAtLeast(.25f)
            val diameter = if (stroke.dualSpray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.dualParticleSize(stroke, point), stroke.dualSprayParticleSizeUnit, stroke.dualSprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.dualSpray) SprayGeometry.offset(brushDiameter / 2f * stroke.dualSpraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.dualSprayBias) else SprayOffset(0f, 0f)
            val drawX = point.x * width + offset.x
            val drawY = point.y * height + offset.y
            val thicknessDynamic = dynamics.thickness
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, stroke.dualThickness.coerceIn(.05f, 2f) * thicknessDynamic, stroke.dualThicknessVertical)
            val flip = BrushTipFlip.signs(stroke.dualTipFlipHorizontalMode, stroke.dualTipFlipVerticalMode, stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, dual = true)
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = true)
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = true,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val matrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x * flip.x, tipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
            paint.colorFilter = PorterDuffColorFilter(BrushColorJitter.argb(stroke, point, sequenceIndex), PorterDuff.Mode.SRC_IN)
            paint.alpha = (stroke.dualFlow.coerceIn(0f, 1f) * dynamics.depositedAlpha * (falloff?.get(index) ?: 1f)).coerceIn(0f, 1f).times(255).toInt()
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * width / 1600f * stroke.dualSizeScale.coerceIn(.01f, 8f), width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        paint.blendMode = null
        canvas.restoreToCount(dualLayer)
    }

    private fun drawLinePoint(canvas: Canvas, paint: Paint, stroke: InkStroke, point: StrokePoint, x: Float, y: Float, strokeWidth: Float, alpha: Int, pageWidth: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (hasWaterEdge(stroke, point)) {
            configureWaterEdge(paint, stroke, point, strokeWidth, alpha, pageWidth, jitteredColor)
            canvas.drawPoint(x, y, paint)
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * pageWidth / 1600f, pageWidth.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawPoint(x, y, paint)
        paint.maskFilter = null
    }

    private fun drawLineSegment(canvas: Canvas, paint: Paint, stroke: InkStroke, point: StrokePoint, startX: Float, startY: Float, endX: Float, endY: Float, strokeWidth: Float, alpha: Int, pageWidth: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (hasWaterEdge(stroke, point)) {
            configureWaterEdge(paint, stroke, point, strokeWidth, alpha, pageWidth, jitteredColor)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * pageWidth / 1600f, pageWidth.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.maskFilter = null
    }

    private fun brushBlurMask(stroke: InkStroke, point: StrokePoint, diameter: Float, baseDiameter: Float, pageWidth: Float): BlurMaskFilter? {
        val gradation = stroke.tiltGradation.coerceIn(0f, 1f) * BrushTilt.response(point, stroke.tiltGradationStart)
        val radius = BrushBlur.radiusPixels(stroke.blur, stroke.blurUnit, stroke.blurLinkedToSize, diameter, baseDiameter, pageWidth) *
            EngineV2BrushEvaluator.evaluateDab(stroke, point).blur + diameter * gradation * .22f
        return BrushBlur.quantizedRadius(radius).takeIf { it > 0f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
    }

    private fun hasWaterEdge(stroke: InkStroke, point: StrokePoint): Boolean =
        (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) ||
            (stroke.tiltBleed > 0f && BrushTilt.response(point, stroke.tiltBleedStart) > .001f)

    private fun configureWaterEdge(paint: Paint, stroke: InkStroke, point: StrokePoint, strokeWidth: Float, alpha: Int, pageWidth: Int, color: Int) {
        val tiltBleedAmount = stroke.tiltBleed.coerceIn(0f, 1f) * BrushTilt.response(point, stroke.tiltBleedStart)
        val radius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, pageWidth.toFloat()) + strokeWidth * tiltBleedAmount * .18f
        paint.color = WaterEdge.darkenArgb(color, stroke.waterEdgeDarkness)
        paint.strokeWidth = strokeWidth + radius * 2f
        paint.alpha = (alpha * maxOf(stroke.waterEdgeOpacity.coerceIn(0f, 1f), tiltBleedAmount * .55f)).toInt().coerceIn(1, 255)
        val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, pageWidth.toFloat()) + strokeWidth * tiltBleedAmount * .08f
        paint.maskFilter = BrushBlur.quantizedRadius(blur).takeIf { it > 0f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
    }

    private fun decodeTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, antiAliasMode: Int = CspAntiAlias.MEDIUM): Bitmap? =
        BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, antiAliasMode)

    private fun rotateQuarterTurns(source: Bitmap?, quarterTurns: Int): Bitmap? {
        source ?: return null
        val turns = quarterTurns.mod(4)
        if (turns == 0) return source
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(turns * 90f) }, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun cropBackground(source: Bitmap?, cropValue: PageCrop, targetWidth: Int): Bitmap? {
        source ?: return null
        val crop = PageCropTransform.normalized(cropValue)
        if (crop == PageCrop()) return source
        val left = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (crop.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (crop.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        if (cropped !== source) source.recycle()
        val scaledHeight = (cropped.height * targetWidth.toFloat() / cropped.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, scaledHeight, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun formatDuration(durationMs: Long): String { val total = durationMs / 1000; return "%d:%02d".format(total / 60, total % 60) }

    private fun midpoint(start: StrokePoint, end: StrokePoint) = StrokePoint(
        x = (start.x + end.x) / 2f, y = (start.y + end.y) / 2f,
        pressure = (start.pressure + end.pressure) / 2f,
        timeMs = (start.timeMs + end.timeMs) / 2L,
        tilt = (start.tilt + end.tilt) / 2f,
        orientation = (start.orientation + end.orientation) / 2f,
        velocity = (start.velocity + end.velocity) / 2f,
        pigmentArgb = when {
            start.pigmentArgb == null -> end.pigmentArgb
            end.pigmentArgb == null -> start.pigmentArgb
            else -> blendArgb(start.pigmentArgb, end.pigmentArgb, .5f)
        },
    )

    private fun blendArgb(from: Long, to: Long, amount: Float): Long {
        fun channel(shift: Int): Long {
            val first = from shr shift and 0xFF
            val second = to shr shift and 0xFF
            return (first + (second - first) * amount.coerceIn(0f, 1f)).toLong().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private companion object {
        const val DUAL_SCATTER_SEED = 0x27C4915DL
        const val DUAL_ROTATION_SEED = 0x61B8D2A3L
        const val DUAL_SEQUENCE_SEED = 0x35A71C9E
        const val MAX_DYNAMIC_GRAIN_VARIANTS_PER_STROKE = 64
        const val PDF_TEXT_INDEX_VERSION = 1
        const val PDF_LINK_INDEX_VERSION = 1
        const val MAX_PDF_TEXT_CHARACTERS_PER_PAGE = 1_000_000
        const val MAX_PDF_TEXT_BLOCKS_PER_PAGE = 20_000
        const val MAX_PDF_BOUNDS_PER_BLOCK = 4_096
        const val MAX_PDF_BOUNDS_LOOKUPS_PER_PAGE = 512
        const val MAX_PDF_SEARCH_NEEDLE_CHARACTERS = 128
        const val MAX_PDF_GOTO_LINKS_PER_PAGE = 512
        const val MAX_PDF_BOUNDS_PER_LINK = 16
    }

    private fun copyAsset(uri: Uri, extension: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val temporary = File(directory, ".${System.nanoTime()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count); output.write(buffer, 0, count) }
        } } ?: error("Selected asset is unreadable")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val target = File(directory, "$hash.${extension.lowercase().filter(Char::isLetterOrDigit).ifBlank { "asset" }}")
        if (target.exists()) temporary.delete() else check(temporary.renameTo(target)) { "Could not retain imported asset" }
        return target
    }

}

private data class PdfDestinationPage(
    val id: String,
    val width: Float,
    val height: Float,
    val rotationQuarterTurns: Int = 0,
)

private data class PdfPageIndexUpdate(
    val textBlocks: List<PdfTextBlock>,
    val gotoLinks: List<PdfGotoLink>,
)
