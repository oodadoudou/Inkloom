package app.inkloom.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.inkloom.engine.v5.storage.DocumentCheckpointCodec
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.android.PixelTileCanvasRenderer
import app.inkloom.engine.v5.android.LayerContentProjectionCache
import app.inkloom.engine.v5.composite.PixelGroupProjectionCache
import app.inkloom.engine.v5.composite.PixelProjectionNode
import app.inkloom.engine.v5.composite.PixelLayerProjectionInput
import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import app.inkloom.engine.v5.storage.MaskTileArchiveCodec
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelRect
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

internal const val MAX_NOTEBOOK_PAGES = 20_000

class DocumentAssetService(private val context: Context) {
    private val directory = File(context.filesDir, "document-assets").apply { mkdirs() }
    private val pixelTileDirectory = File(directory, "pixel-tiles")
    private val pixelTileStore = PixelTileArchiveStore(pixelTileDirectory)
    private val assetLifecycle = DocumentAssetLifecycles.forDirectory(directory, pixelTileStore)
    private val brushAssetDirectory = File(context.filesDir, "brush-assets").apply { mkdirs() }
    @androidx.annotation.VisibleForTesting
    internal var v5RasterProjectionExportCount: Int = 0

    fun importPdf(uri: Uri, title: String, indexText: Boolean = true, onProgress: (Int, Int) -> Unit = { _, _ -> }, checkCancelled: () -> Unit = {}): InkDocument {
        val asset = copyAsset(uri, "pdf", checkCancelled)
        val descriptor = ParcelFileDescriptor.open(asset, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                val count = renderer.pageCount
                require(count in 1..MAX_NOTEBOOK_PAGES) { "PDF has $count pages; the supported limit is $MAX_NOTEBOOK_PAGES" }
                val pageIds = List(count) { UUID.randomUUID().toString() }
                val destinationPages = if (indexText) pageIds.mapIndexed { index, id ->
                    checkCancelled()
                    renderer.openPage(index).use { page -> index to PdfDestinationPage(id, page.width.toFloat(), page.height.toFloat()) }
                }.toMap() else emptyMap()
                val pages = ArrayList<InkPage>(count)
                onProgress(0, count)
                for (index in 0 until count) {
                    checkCancelled()
                    pages += renderer.openPage(index).use { page ->
                        val textBlocks = if (indexText) extractPdfText(page) else null
                        val gotoLinks = if (indexText) extractPdfGotoLinks(page, destinationPages) else null
                        InkPage(
                            id = pageIds[index], title = "Page ${index + 1}",
                            width = page.width.toFloat(), height = page.height.toFloat(), dpi = 72f,
                            background = PageBackground.Pdf(asset.absolutePath, index),
                            pdfTextBlocks = textBlocks.orEmpty(),
                            pdfTextIndexVersion = if (textBlocks != null) PDF_TEXT_INDEX_VERSION else 0,
                            pdfGotoLinks = gotoLinks.orEmpty(),
                            pdfLinkIndexVersion = if (gotoLinks != null) PDF_LINK_INDEX_VERSION else 0,
                        )
                    }
                    onProgress(index + 1, count)
                }
                checkCancelled()
                return InkDocument(title = title.substringBeforeLast('.').ifBlank { "Imported PDF" }, coverArgb = 0xFF707070, pages = pages)
            } finally { renderer.close() }
        } finally { descriptor.close() }
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
        val (width, height) = ImagePaperRenderer.dimensions(asset)
        return InkDocument(title = title.substringBeforeLast('.').ifBlank { "Imported Image" }, pages = listOf(InkPage(width = width.toFloat(), height = height.toFloat(), background = PageBackground.Image(asset.absolutePath))))
    }

    /** Prepares new full pages for an existing notebook without publishing a partial import. */
    fun importAdditionalPages(
        uri: Uri,
        displayName: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        checkCancelled: () -> Unit = {},
    ): List<InkPage> {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val name = displayName.trim().take(120).ifBlank { "Imported file" }
        val source = when {
            mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
                importPdf(uri, name, indexText = true, onProgress = onProgress, checkCancelled = checkCancelled)
            name.substringAfterLast('.').lowercase() in setOf("psd", "ora", "tif", "tiff", "zip") -> importArtwork(uri, name).also { onProgress(1, 1) }
            mime.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "heic", "heif", "bmp", "gif") -> {
                checkCancelled()
                importImage(uri, name).also { onProgress(1, 1) }
            }
            name.substringAfterLast('.').lowercase() in setOf("txt", "md", "markdown") || mime == "text/plain" || mime == "text/markdown" -> {
                val pages = context.contentResolver.openInputStream(uri)?.use { TextPageImporter.read(it, name) } ?: error("Unreadable text file")
                InkDocument(title = name, pages = pages).also { onProgress(pages.size, pages.size) }
            }
            else -> throw IllegalArgumentException("Unsupported file: $name. Import PDF, artwork, images or UTF-8 text/Markdown.")
        }
        checkCancelled()
        val base = name.substringBeforeLast('.').ifBlank { name }
        return source.pages.mapIndexed { index, page ->
            page.copy(title = if (source.pages.size == 1) base else "$base · ${index + 1}")
        }
    }

    fun prepareImagePaper(uri: Uri, name: String): ImagePaperDraft {
        val asset = copyAsset(uri, "paper")
        val release = retainHistoryAssets(listOf(asset.absolutePath))
        try {
            val (width, height) = ImagePaperRenderer.dimensions(asset)
            return ImagePaperDraft(PageTemplate(name = name.trim().take(80).ifBlank { "Image paper" },
                width = width.toFloat(), height = height.toFloat(), background = PageBackground.Image(asset.absolutePath)), release)
        } catch (failure: Throwable) { release(); throw failure }
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

    /** Persists authoritative V5 sparse fill alpha without materializing a page-sized bitmap. */
    fun saveFillMask(mask: MaskTileSurfaceSnapshot): String =
        assetLifecycle.saveBytes(MaskTileArchiveCodec.encode(mask), "fill-mask.v5")

    fun saveMaskTileSurface(mask: MaskTileSurfaceSnapshot): MaskTileSurfaceRef {
        val path = assetLifecycle.saveBytes(MaskTileArchiveCodec.encode(mask), "layer-mask.v5")
        return MaskTileSurfaceRef(
            assetPath = path,
            surfaceId = mask.surfaceId,
            logicalWidth = mask.logicalWidth,
            logicalHeight = mask.logicalHeight,
            tileSize = mask.tileSize,
            revision = mask.revision,
            defaultAlpha = mask.defaultAlpha,
            tileCount = mask.tileCount,
        )
    }

    fun loadMaskTileSurface(reference: MaskTileSurfaceRef): MaskTileSurface {
        val surface = requireNotNull(loadSparseFillMask(reference.assetPath)) { "Layer mask asset is unavailable" }
        val snapshot = surface.snapshot()
        require(snapshot.surfaceId == reference.surfaceId &&
            snapshot.logicalWidth == reference.logicalWidth &&
            snapshot.logicalHeight == reference.logicalHeight &&
            snapshot.tileSize == reference.tileSize &&
            snapshot.defaultAlpha == reference.defaultAlpha &&
            snapshot.tileCount == reference.tileCount
        ) { "Layer mask asset does not match its document reference" }
        return surface
    }

    /** Atomically publishes an immutable sparse surface generation and reserves it until committed. */
    fun saveRasterTileSurface(
        surface: PixelTileSurface,
        previousReference: RasterTileSurfaceRef? = null,
    ): RasterTileSurfaceRef = assetLifecycle.saveRasterTileSurface(surface, previousReference)

    /** Imports an external bitmap into the native V5 sparse tile format. */
    fun importRasterTileSurface(bitmap: Bitmap): RasterTileSurfaceRef {
        val surface = PixelTileBitmapAdapter.import(bitmap)
        return saveRasterTileSurface(surface)
    }

    /** Reopens and validates the exact immutable surface generation referenced by a document. */
    fun loadRasterTileSurface(reference: RasterTileSurfaceRef): PixelTileSurface {
        val manifest = File(reference.manifestAssetPath)
        val metadata = pixelTileStore.inspect(manifest)
        require(metadata.surfaceId == reference.surfaceId &&
            metadata.logicalWidth == reference.logicalWidth &&
            metadata.logicalHeight == reference.logicalHeight &&
            metadata.tileSize == reference.tileSize &&
            metadata.revision == reference.revision &&
            metadata.pixelFormat.manifestId == reference.pixelFormat
        ) { "Raster tile manifest does not match its document reference" }
        return pixelTileStore.load(manifest)
    }

    internal fun referencedRasterTileFiles(reference: RasterTileSurfaceRef): List<File> {
        loadRasterTileSurface(reference)
        return pixelTileStore.referencedFiles(File(reference.manifestAssetPath))
    }

    fun retainHistoryAssets(paths: List<String>): () -> Unit = assetLifecycle.retainHistoryAssets(paths)

    fun releaseAssetReservation(path: String?) = assetLifecycle.releaseAssetReservation(path)

    fun releaseRasterTileReservation(reference: RasterTileSurfaceRef?) =
        assetLifecycle.releaseRasterTileReservation(reference)

    fun loadSparseFillMask(maskAssetPath: String): MaskTileSurface? = runCatching {
        val bytes = File(maskAssetPath).readBytes()
        if (MaskTileArchiveCodec.isSparseMask(bytes)) MaskTileArchiveCodec.decode(bytes) else null
    }.getOrNull()

    /** Draws one authoritative V5 sparse alpha mask. */
    fun drawFillMask(canvas: Canvas, maskAssetPath: String, destination: RectF, paint: Paint): Boolean {
        loadSparseFillMask(maskAssetPath)?.let { surface ->
            val mask = SparseAlphaMask.fromSurface(surface) ?: return false
            mask.draw(canvas, destination, paint)
            return true
        }
        return false
    }

    /** Removes only private document assets that are no longer referenced by a document or template. */
    fun garbageCollectUnreferencedAssets(
        documents: List<InkDocument>,
        pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
    ): Int = assetLifecycle.garbageCollect(documents, pageTemplates)

    fun importCoverImage(uri: Uri, crop: PageCrop? = null): String {
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/')?.take(8).orEmpty().ifBlank { "image" }
        val asset = copyAsset(uri, extension)
        try { CoverImageDecoder.decode(asset, 64).recycle() }
        catch (failure: Exception) {
            // copyAsset is content-addressed; an existing notebook may own the same file.
            // The normal reference-aware collector will remove it only if it is unused.
            throw IllegalArgumentException("Selected cover could not be decoded", failure)
        }
        if (crop == null) return asset.absolutePath
        require(crop.left >= 0f && crop.top >= 0f && crop.right <= 1f && crop.bottom <= 1f &&
            crop.right > crop.left && crop.bottom > crop.top) { "Invalid cover crop" }
        val source = CoverImageDecoder.decode(asset, 2048)
        try {
            val left = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
            val top = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
            val right = (crop.right * source.width).toInt().coerceIn(left + 1, source.width)
            val bottom = (crop.bottom * source.height).toInt().coerceIn(top + 1, source.height)
            val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            try {
                val temporary = File(directory, ".${UUID.randomUUID()}.tmp")
                try {
                    temporary.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    val target = File(directory, "${UUID.randomUUID()}.png")
                    check(temporary.renameTo(target)) { "Could not save cropped cover" }
                    return target.absolutePath
                } finally { temporary.delete() }
            } finally { if (cropped !== source) cropped.recycle() }
        } finally { source.recycle() }
    }

    fun renderBackground(background: PageBackground, targetWidth: Int, rotationQuarterTurns: Int = 0, crop: PageCrop = PageCrop()): Bitmap? {
        val sourceBitmap = when (background) {
        is PageBackground.Paper -> null
        is PageBackground.Image -> ImagePaperRenderer.render(background, targetWidth)
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
                RecognizedTextSource.HANDWRITING -> page.handwritingIndexFingerprint == HandwritingIndex.fingerprint(page)
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

    fun renderBackgroundForOcr(page: InkPage): Bitmap {
        val aspect = PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page)
        val width = minOf(2048, kotlin.math.sqrt(8_388_608.0 / aspect).toInt(), (8192 / aspect).toInt()).coerceAtLeast(1)
        val height = (width * aspect).toInt().coerceIn(1,8192)
        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try { PageBackgroundStripRenderer(page,width,height).use { it.draw(bitmap,0) }; return bitmap }
        catch(failure:Throwable) { bitmap.recycle(); throw failure }
    }

    fun exportPageRaster(page: InkPage, uri: Uri, options: PageRasterExportOptions) = RasterFormatService(context, this).export(page, uri, options)
    fun importArtwork(uri: Uri, name: String): InkDocument = RasterFormatService(context, this).import(uri, name)

    fun exportPagePng(page: InkPage, uri: Uri, options: PageRasterExportOptions = PageRasterExportOptions()) {
        val authored = PagePngExportSize.from(page)
        val width = options.width ?: authored.width; val height = options.height ?: authored.height
        require(width in 1..8192 && height in 1..8192)
        val projection = renderPageCompositeSurface(page, width, height)
        val tileHeight = 128
        var strip: Bitmap? = null
        var stripStart = -1
        val backgroundRenderer = if (options.includeBackground) PageBackgroundStripRenderer(page, width, height) else null
        PixelTileCanvasRenderer().use { renderer ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    StreamingPng.write(output, width, height, page.dpi) { y, row ->
                        if (strip == null || y >= stripStart + requireNotNull(strip).height) {
                            strip?.recycle(); stripStart = y
                            strip = Bitmap.createBitmap(width, minOf(tileHeight, height - y), Bitmap.Config.ARGB_8888)
                            backgroundRenderer?.draw(requireNotNull(strip), y)
                            val canvas = Canvas(requireNotNull(strip)); canvas.translate(0f, -y.toFloat())
                            renderer.draw(canvas, projection, RectF(0f, 0f, width.toFloat(), height.toFloat()))
                        }
                        requireNotNull(strip).getPixels(row, 0, width, 0, y - stripStart, width, 1)
                    }
                } ?: error("Could not open image destination")
            } finally { strip?.recycle(); backgroundRenderer?.close() }
        }
    }

    /** Fully prepares a reversible replacement before publishing any new page metadata. */
    fun prepareCanvasSize(page: InkPage, request: CanvasSizeRequest): PreparedPageGeometry {
        val (width, height) = request.pixels()
        if (page.crop == PageCrop() && width.toFloat() == page.width && height.toFloat() == page.height) {
            return PreparedPageGeometry(PageGeometryEdit(page, page.copy(dpi = request.dpi))) {}
        }
        val visible = PageCropTransform.project(page)
        val sx = if (request.mode == CanvasResizeMode.RESAMPLE) width / visible.width else 1f
        val sy = if (request.mode == CanvasResizeMode.RESAMPLE) height / visible.height else 1f
        val offset = if (request.mode == CanvasResizeMode.CANVAS)
            CanvasSizing.anchorOffset(request.anchor, visible.width, visible.height, width.toFloat(), height.toFloat()) else 0f to 0f
        val rasterReservations = mutableListOf<RasterTileSurfaceRef>()
        val maskReservations = mutableListOf<String>()
        var releaseHistory: () -> Unit = {}
        fun close() { releaseHistory(); rasterReservations.forEach(::releaseRasterTileReservation); maskReservations.forEach(::releaseAssetReservation) }
        try {
            page.layers.mapNotNull { it.rasterTileSurface }.forEach { reference ->
                rasterReservations += saveRasterTileSurface(loadRasterTileSurface(reference), reference)
            }
            fun resizedMask(reference: MaskTileSurfaceRef, saved: SavedSelection? = null): MaskTileSurfaceRef {
                val original = loadMaskTileSurface(reference).snapshot()
                val source = if (saved == null) original else SavedSelectionGeometry.projected(original, saved.rotationQuarterTurns, saved.crop, page.crop, visible.width.toInt(), visible.height.toInt())
                val mask = MaskTileSurface(width, height, initialDefaultAlpha = source.defaultAlpha)
                val mx = (if (saved == null) page.width else visible.width) / source.logicalWidth * sx
                val my = (if (saved == null) page.height else visible.height) / source.logicalHeight * sy
                val dx = offset.first - (if (saved == null) page.crop.left * page.width * sx else 0f)
                val dy = offset.second - (if (saved == null) page.crop.top * page.height * sy else 0f)
                val keys = linkedSetOf<app.inkloom.engine.v5.surface.PixelTileKey>()
                source.tileInfo.forEach { info ->
                    val l = kotlin.math.floor(info.key.column * source.tileSize * mx + dx - 1).toInt().coerceIn(0, width)
                    val t = kotlin.math.floor(info.key.row * source.tileSize * my + dy - 1).toInt().coerceIn(0, height)
                    val r = kotlin.math.ceil((info.key.column + 1) * source.tileSize * mx + dx + 1).toInt().coerceIn(0, width)
                    val b = kotlin.math.ceil((info.key.row + 1) * source.tileSize * my + dy + 1).toInt().coerceIn(0, height)
                    if (r > l && b > t) for (y in t / mask.tileSize..(b - 1) / mask.tileSize)
                        for (x in l / mask.tileSize..(r - 1) / mask.tileSize) keys += app.inkloom.engine.v5.surface.PixelTileKey(x, y)
                }
                mask.beginWrite("resize mask").use { write ->
                    keys.forEach { key ->
                        val tw = minOf(mask.tileSize, width - key.column * mask.tileSize)
                        val th = minOf(mask.tileSize, height - key.row * mask.tileSize)
                        write.editTile(key, PixelRect(0, 0, tw, th)) { pixels, stride ->
                            for (y in 0 until th) for (x in 0 until tw) {
                                val ox = kotlin.math.floor((key.column * mask.tileSize + x + .5f - dx) / mx).toInt()
                                val oy = kotlin.math.floor((key.row * mask.tileSize + y + .5f - dy) / my).toInt()
                                pixels[y * stride + x] = (if (ox in 0 until source.logicalWidth && oy in 0 until source.logicalHeight)
                                    source.alphaAt(ox, oy) else source.defaultAlpha).toByte()
                            }
                        }
                    }
                    write.commit().close()
                }
                return saveMaskTileSurface(mask.snapshot()).also { maskReservations += it.assetPath }
            }
            val layers = visible.layers.map { layer ->
                val raster = layer.rasterTileSurface?.let {
                    val original = renderRasterLayerSurface(page, layer.id, visible.width.toInt().coerceAtLeast(1))
                    val resized = app.inkloom.engine.v5.tools.PixelSurfaceResampling.resample(original.snapshot(), width, height,
                        sx, sy, offset.first, offset.second)
                    saveRasterTileSurface(resized).also(rasterReservations::add)
                }
                layer.copy(rasterTileSurface = raster, rasterRotationQuarterTurns = 0,
                    mask = layer.mask?.let { it.copy(surface = it.surface?.let { ref -> resizedMask(ref) }) })
            }
            val transform = app.inkloom.engine.v5.tools.SelectionAffineTransform(0f, 0f, sx, sy, 0f, offset.first, offset.second)
            val elements = visible.elements.map { original ->
                val mapped = StructuredElementTransform.map(original, transform, visible.width.toInt(), visible.height.toInt(), visible.width, visible.height)
                val normalized = PageCropTransform.unproject(mapped, PageCrop())
                val x = normalized.x * visible.width / width; val y = normalized.y * visible.height / height
                when (normalized) {
                    is PageElement.Text -> normalized.copy(x = x, y = y, textSize = if (normalized.deformation == null) (original as PageElement.Text).textSize * sx else (original as PageElement.Text).textSize)
                    is PageElement.Sticky -> normalized.copy(x = x, y = y)
                    is PageElement.Shape -> normalized.copy(x = x, y = y, strokeWidth = (original as PageElement.Shape).strokeWidth * if (request.mode == CanvasResizeMode.RESAMPLE) sx else 1f)
                    is PageElement.Image -> normalized.copy(x = x, y = y)
                    is PageElement.Fill -> normalized.copy(x = x, y = y)
                    is PageElement.Link -> normalized.copy(x = x, y = y)
                }
            }
            val previousFrame = visible.backgroundFrame ?: PageBackgroundFrame(0f, 0f, visible.width, visible.height)
            fun bounds(b: PdfTextBounds): PdfTextBounds = PdfTextBounds(
                (b.left * visible.width * sx + offset.first) / width,
                (b.top * visible.height * sy + offset.second) / height,
                (b.right * visible.width * sx + offset.first) / width,
                (b.bottom * visible.height * sy + offset.second) / height)
            val after = visible.copy(width = width.toFloat(), height = height.toFloat(), dpi = request.dpi,
                layers = layers, elements = elements, crop = PageCrop(),
                backgroundFrame = PageBackgroundFrame(previousFrame.left * sx + offset.first, previousFrame.top * sy + offset.second,
                    previousFrame.width * sx, previousFrame.height * sy),
                backgroundSourceCrop = page.backgroundSourceCrop ?: page.crop,
                pdfTextBlocks = visible.pdfTextBlocks.map { it.copy(bounds = it.bounds.map(::bounds)) },
                pdfGotoLinks = visible.pdfGotoLinks.map { it.copy(bounds = it.bounds.map(::bounds)) },
                recognizedTextBlocks = emptyList(), handwritingIndexFingerprint = "", imageOcrFingerprint = "",
                savedSelections = visible.savedSelections.map { it.copy(surface = resizedMask(it.surface, it), crop = PageCrop(), rotationQuarterTurns = 0) })
            releaseHistory = retainHistoryAssets((page.layers + after.layers).mapNotNull { it.mask?.surface?.assetPath } +
                (page.savedSelections + after.savedSelections).map { it.surface.assetPath })
            return PreparedPageGeometry(PageGeometryEdit(page, after), ::close)
        } catch (failure: Throwable) { close(); throw failure }
    }

    /** Same group, mask, clipping and adjustment semantics, with bounded object rasterization. */
    fun renderPageCompositeSurface(source: InkPage, width: Int, height: Int): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot {
        require(width in 1..8192 && height in 1..8192)
        val page = PageCropTransform.project(source)
        val cache = PixelGroupProjectionCache(width, height)
        fun mask(layer: InkLayer) = layer.mask?.takeIf { it.enabled }?.let { m ->
            m.surface?.let { m.expressed(loadMaskTileSurface(it).snapshot()) }
        }
        val inputs = page.layers.filter { it.isPaintSurfaceLayer() && LayerHierarchy.effectiveVisible(page.layers, it) && !it.draft }.associate { layer ->
            val reference = layer.rasterTileSurface
            val raw = if (reference != null) {
                val original = renderRasterLayerSurface(source, layer.id, width).snapshot()
                if (original.logicalHeight == height && original.tileSize == PixelTileSurface.DEFAULT_TILE_SIZE) original
                else app.inkloom.engine.v5.tools.PixelSurfaceResampling.resample(original, width, height,
                    1f, height.toFloat() / original.logicalHeight, targetTileSize = PixelTileSurface.DEFAULT_TILE_SIZE).snapshot()
            } else null
            val ids = page.elements.filter { it.layerId == layer.id }.mapTo(hashSetOf(), PageElement::id)
            val captured = layer.alphaLockBaseContentIds.toSet().takeIf { layer.alphaLock && it.isNotEmpty() }
            val nodes = mutableListOf<PixelProjectionNode>()
            val base = buildList<PixelProjectionNode> {
                raw?.let { add(PixelProjectionNode.Paint(PixelLayerProjectionInput("raw", it))) }
                if (ids.isNotEmpty()) add(PixelProjectionNode.Paint(PixelLayerProjectionInput("objects",
                    renderElementLayerSurface(source, layer.id, width, height, captured))))
            }
            nodes += PixelProjectionNode.Group("base", base)
            if (captured != null && (ids - captured).isNotEmpty()) nodes += PixelProjectionNode.Paint(PixelLayerProjectionInput("additions",
                renderElementLayerSurface(source, layer.id, width, height, ids - captured), clipToBaseBelow = true))
            val content = if (ids.isEmpty()) raw ?: cache.emptySnapshot else PixelGroupProjectionCache(width, height).update(nodes).snapshot
            layer.id to PixelLayerProjectionInput(layer.id, content, opacity = layer.opacity,
                blendMode = layer.blendMode.toPixelBlendMode(), mask = mask(layer),
                clipToBaseBelow = layer.clippedToBelow, colorReplacement = layer.colorReplacement)
        }
        val nodes = LayerCompositingTree.project<PixelProjectionNode>(page.layers,
            paint = { layer -> when {
                layer.role == LayerRole.BACKGROUND -> null
                layer.role == LayerRole.ADJUSTMENT && layer.adjustment != null -> PixelProjectionNode.Adjustment(
                    layer.id, layer.adjustment, layer.opacity, layer.blendMode.toPixelBlendMode(), mask(layer), layer.visible && !layer.draft)
                else -> inputs[layer.id]?.let(PixelProjectionNode::Paint)
            } },
            group = { layer, children -> PixelProjectionNode.Group(layer.id, children, layer.opacity, layer.blendMode.toPixelBlendMode(),
                mask(layer), layer.clippedToBelow, layer.visible && !layer.draft, layer.passThrough) })
        return cache.update(nodes).snapshot
    }

    fun renderElementLayerSurface(page: InkPage, layerId: String, width: Int, height: Int,
                                         contentIds: Set<String>? = null): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot {
        val surface = PixelTileSurface(width, height)
        val step = surface.tileSize
        surface.beginWrite("render retained elements").use { write ->
            for (top in 0 until height step step) {
                val bitmap = Bitmap.createBitmap(width, minOf(step, height - top), Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap); canvas.translate(0f, -top.toFloat())
                    drawPage(canvas, page, width, height, includeBackground = false, includedLayerIds = setOf(layerId),
                        includedContentIds = contentIds, includeRaster = false, applyLayerEffects = false, includeDraftLayers = true)
                    val strip = PixelTileBitmapAdapter.import(bitmap, step).snapshot()
                    strip.tileInfo.forEach { info -> strip.readRgba8Tile(info.key) { pixels ->
                        write.editTile(app.inkloom.engine.v5.surface.PixelTileKey(info.key.column, top / step),
                            PixelRect(0, 0, minOf(step, width - info.key.column * step), bitmap.height)) { target, _ -> pixels.copyInto(target) }
                    } }
                } finally { bitmap.recycle() }
            }
            write.commit().close()
        }
        return surface.snapshot()
    }

    fun renderPageBitmap(page: InkPage, width: Int): Bitmap {
        require(width in 1..8192) { "Render width must be between 1 and 8192 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        return renderPageBitmap(page, width, height)
    }

    private fun renderPageBitmap(page: InkPage, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(Canvas(bitmap), page, width, height)
        return bitmap
    }

    /** Bake one planned merge through the same compositor used by export and the live projection. */
    fun renderLayerMergeSurface(plan: RasterLayerMerge.Plan): PixelTileSurface {
        val page = plan.renderPage
        val width = plan.renderWidth
        val height = (width * page.height / page.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            drawPage(
                Canvas(bitmap),
                page,
                width,
                height,
                includeBackground = plan.includeBackground,
                includeDraftLayers = false,
            )
            PixelTileBitmapAdapter.import(bitmap)
        } finally { bitmap.recycle() }
    }

    /** Small isolated preview: hidden layers remain identifiable without changing document state. */
    fun renderLayerThumbnail(page: InkPage, layerId: String, size: Int = 96): Bitmap {
        require(size in 16..256)
        val layer = requireNotNull(page.layers.firstOrNull { it.id == layerId })
        val visibleWidth = PageCropTransform.visibleWidth(page).coerceAtLeast(1f)
        val visibleHeight = PageCropTransform.visibleHeight(page).coerceAtLeast(1f)
        val scale = size / maxOf(visibleWidth, visibleHeight)
        val width = (visibleWidth * scale).toInt().coerceAtLeast(1)
        val height = (visibleHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val isolated = page.copy(layers = listOf(layer.copy(visible = true, parentId = null,
            opacity = 1f, blendMode = LayerBlendMode.NORMAL, clippedToBelow = false)))
        drawPage(Canvas(bitmap), isolated, width, height, includeBackground = false,
            includedLayerIds = setOf(layerId), includeDraftLayers = true)
        return bitmap
    }

    fun renderElementLayerBitmap(page: InkPage, layerId: String, width: Int, contentIds: Set<String>? = null): Bitmap {
        require(width in 1..8192) { "Layer render width must be between 1 and 8192 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(
            Canvas(bitmap), page, width, height,
            includeBackground = false,
            includedLayerIds = setOf(layerId),
            includedContentIds = contentIds,
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
        require(width in 1..8192) { "Layer render width must be between 1 and 8192 pixels" }
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawPage(
            Canvas(bitmap), page, width, height,
            includeBackground = false,
            includedLayerIds = setOf(layerId),
            // Editable pixel ownership excludes retained image/shape/fill objects. Those
            // have their own compositor plane and must not be baked a second time on load.
            includedContentIds = emptySet(),
            includeRaster = true,
            applyLayerEffects = false,
            includeDraftLayers = true,
        )
        return bitmap
    }

    /**
     * Builds the visible, presentation-oriented sparse projection consumed by the live canvas.
     * The temporary bitmap is an Android decode/resample boundary only and is released before this
     * method returns; callers retain no page-sized bitmap mirror.
     */
    fun renderRasterLayerSurface(page: InkPage, layerId: String, width: Int): PixelTileSurface {
        val sourceLayer = page.layers.firstOrNull { it.id == layerId }
        val reference = sourceLayer?.rasterTileSurface
        val height = (width * PageCropTransform.visibleHeight(page) / PageCropTransform.visibleWidth(page)).toInt().coerceAtLeast(1)
        if (sourceLayer != null && reference == null) return PixelTileSurface(width, height)
        if (reference != null && reference.pixelFormat == app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_SRGB_8888.manifestId) {
            val surface = loadRasterTileSurface(reference)
            if (page.crop == PageCrop() && sourceLayer.rasterRotationQuarterTurns.mod(4) == 0 &&
                surface.logicalWidth == width && surface.logicalHeight == height) return surface
            val crop = PageCropTransform.normalized(page.crop)
            return app.inkloom.engine.v5.tools.PixelSurfaceResampling.project(surface.snapshot(), width, height,
                sourceLayer.rasterRotationQuarterTurns, crop.left, crop.top, crop.right, crop.bottom)
        }
        val projection = renderRasterLayerBitmap(page, layerId, width)
        return try {
            PixelTileBitmapAdapter.import(projection)
        } finally {
            projection.recycle()
        }
    }

    private fun renderRasterTileReference(
        reference: RasterTileSurfaceRef,
        rotationQuarterTurns: Int,
        crop: PageCrop,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val snapshot = loadRasterTileSurface(reference).snapshot()
        val turns = rotationQuarterTurns.mod(4)
        val rotatedWidth = if (turns % 2 == 0) snapshot.logicalWidth else snapshot.logicalHeight
        val rotatedHeight = if (turns % 2 == 0) snapshot.logicalHeight else snapshot.logicalWidth
        val normalizedCrop = PageCropTransform.normalized(crop)
        val rotatedRect = PixelRect(
            (normalizedCrop.left * rotatedWidth).toInt().coerceIn(0, rotatedWidth - 1),
            (normalizedCrop.top * rotatedHeight).toInt().coerceIn(0, rotatedHeight - 1),
            (normalizedCrop.right * rotatedWidth).toInt().coerceIn(1, rotatedWidth),
            (normalizedCrop.bottom * rotatedHeight).toInt().coerceIn(1, rotatedHeight),
        )
        require(!rotatedRect.isEmpty) { "Raster crop is empty" }
        val sourceRect = when (turns) {
            0 -> rotatedRect
            1 -> PixelRect(
                rotatedRect.top,
                snapshot.logicalHeight - rotatedRect.right,
                rotatedRect.bottom,
                snapshot.logicalHeight - rotatedRect.left,
            )
            2 -> PixelRect(
                snapshot.logicalWidth - rotatedRect.right,
                snapshot.logicalHeight - rotatedRect.bottom,
                snapshot.logicalWidth - rotatedRect.left,
                snapshot.logicalHeight - rotatedRect.top,
            )
            else -> PixelRect(
                snapshot.logicalWidth - rotatedRect.bottom,
                rotatedRect.left,
                snapshot.logicalWidth - rotatedRect.top,
                rotatedRect.right,
            )
        }
        // Keep source pixels at their authored resolution through the crop and rotation. The
        // previous path asked the tile adapter to scale first; its exact-pixel nearest sampler
        // made legacy 1200 px ink reopen as visible square texels on a 1600 px editing surface.
        val materialized = PixelTileBitmapAdapter.render(snapshot, source = sourceRect)
        val oriented = if (turns == 0) materialized else requireNotNull(rotateQuarterTurns(materialized, turns))
        if (oriented.width == targetWidth && oriented.height == targetHeight) return oriented
        return try {
            Bitmap.createBitmap(targetWidth, targetHeight, oriented.config ?: Bitmap.Config.ARGB_8888).also { resized ->
                Canvas(resized).drawBitmap(
                    oriented,
                    null,
                    android.graphics.Rect(0, 0, targetWidth, targetHeight),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
        } finally {
            oriented.recycle()
        }
    }

    /**
     * Replaces the edited visible crop in the authoritative sparse surface. Existing pixels outside
     * the crop and the layer's non-destructive page rotation are preserved.
     */
    fun composeVisibleRasterTileSurface(
        page: InkPage,
        layerId: String,
        visibleSurface: Bitmap,
    ): PixelTileSurface = composeVisibleRasterTilePatch(
        page = page,
        layerId = layerId,
        patch = visibleSurface,
        patchLeft = 0,
        patchTop = 0,
        visibleSourceWidth = visibleSurface.width,
        visibleSourceHeight = visibleSurface.height,
    )

    /**
     * Replaces one dirty rectangle of the visible crop in the authoritative sparse surface.
     * Patch coordinates are relative to the complete visible-crop source, not to the full page.
     */
    fun composeVisibleRasterTilePatch(
        page: InkPage,
        layerId: String,
        patch: Bitmap,
        patchLeft: Int,
        patchTop: Int,
        visibleSourceWidth: Int,
        visibleSourceHeight: Int,
    ): PixelTileSurface {
        require(!patch.isRecycled) { "Raster patch has been recycled" }
        require(visibleSourceWidth > 0 && visibleSourceHeight > 0) { "Visible source dimensions must be positive" }
        require(patchLeft >= 0 && patchTop >= 0 &&
            patchLeft + patch.width <= visibleSourceWidth && patchTop + patch.height <= visibleSourceHeight
        ) { "Raster patch must fit inside the visible source" }
        val layer = page.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() }
            ?: error("Raster layer does not exist")
        val turns = layer.rasterRotationQuarterTurns.mod(4)
        val crop = PageCropTransform.normalized(page.crop)
        val surface = layer.rasterTileSurface?.let(::loadRasterTileSurface) ?: run {
                val rotatedWidth = (visibleSourceWidth / (crop.right - crop.left).coerceAtLeast(.01f))
                    .toInt().coerceIn(visibleSourceWidth, 8192)
                val rotatedHeight = (visibleSourceHeight / (crop.bottom - crop.top).coerceAtLeast(.01f))
                    .toInt().coerceIn(visibleSourceHeight, 8192)
                PixelTileSurface(
                    logicalWidth = if (turns % 2 == 0) rotatedWidth else rotatedHeight,
                    logicalHeight = if (turns % 2 == 0) rotatedHeight else rotatedWidth,
                    pixelFormat = if (patch.config == Bitmap.Config.RGBA_F16)
                        app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16
                    else app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_SRGB_8888,
                )
        }
        val rotatedWidth = if (turns % 2 == 0) surface.logicalWidth else surface.logicalHeight
        val rotatedHeight = if (turns % 2 == 0) surface.logicalHeight else surface.logicalWidth
        val destination = if (crop == PageCrop() &&
            rotatedWidth == visibleSourceWidth && rotatedHeight == visibleSourceHeight
        ) {
            // Preserve integer patch bounds at 1:1. A float normalization round trip can turn
            // 49 into 48.999996, expand the dirty rect, and resample a stroke during saving.
            PixelRect(patchLeft, patchTop, patchLeft + patch.width, patchTop + patch.height)
        } else {
            val cropWidth = crop.right - crop.left
            val cropHeight = crop.bottom - crop.top
            val normalizedLeft = crop.left + patchLeft.toFloat() / visibleSourceWidth * cropWidth
            val normalizedTop = crop.top + patchTop.toFloat() / visibleSourceHeight * cropHeight
            val normalizedRight = crop.left + (patchLeft + patch.width).toFloat() / visibleSourceWidth * cropWidth
            val normalizedBottom = crop.top + (patchTop + patch.height).toFloat() / visibleSourceHeight * cropHeight
            PixelRect(
                kotlin.math.floor(normalizedLeft * rotatedWidth).toInt().coerceIn(0, rotatedWidth - 1),
                kotlin.math.floor(normalizedTop * rotatedHeight).toInt().coerceIn(0, rotatedHeight - 1),
                kotlin.math.ceil(normalizedRight * rotatedWidth).toInt().coerceIn(1, rotatedWidth),
                kotlin.math.ceil(normalizedBottom * rotatedHeight).toInt().coerceIn(1, rotatedHeight),
            )
        }
        PixelTileBitmapAdapter.replaceRotatedRegion(surface, destination, turns, patch)
        return surface
    }

    fun exportBundle(document: InkDocument, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { exportBundle(document, it) }
            ?: error("Could not open package destination")
    }

    fun exportBundle(document: InkDocument, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun portableRaster(reference: RasterTileSurfaceRef?): RasterTileSurfaceRef? = reference?.let {
                val files = referencedRasterTileFiles(it)
                val manifest = files.first()
                it.copy(manifestAssetPath = "pixel-tiles/manifests/${manifest.name}")
            }
            val portable = document.copy(
                coverImagePath = document.coverImagePath?.let { "assets/${File(it).name}" },
                pages = document.pages.map { page -> page.copy(
                background = when (val background = page.background) {
                    is PageBackground.Pdf -> background.copy(assetPath = "assets/${File(background.assetPath).name}")
                    is PageBackground.Image -> background.copy(assetPath = "assets/${File(background.assetPath).name}")
                    is PageBackground.Paper -> background
                },
                savedSelections = page.savedSelections.map { it.copy(surface = it.surface.copy(assetPath = "assets/${File(it.surface.assetPath).name}")) },
                layers = page.layers.map { layer -> layer.copy(
                    rasterTileSurface = portableRaster(layer.rasterTileSurface),
                    mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                        reference.copy(assetPath = "assets/${File(reference.assetPath).name}")
                    }),
                ) },
                elements = page.elements.map { element -> when (element) {
                    is PageElement.Image -> element.copy(assetPath = "assets/${File(element.assetPath).name}")
                    is PageElement.Fill -> element.copy(maskAssetPath = "assets/${File(element.maskAssetPath).name}")
                    is PageElement.Shape -> element.copy(maskAssetPath = element.maskAssetPath?.let { "assets/${File(it).name}" })
                    else -> element
                } },
                ) },
            )
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(DocumentCheckpointCodec.encode(portable).encodeToByteArray()); zip.closeEntry()
            val assets = listOfNotNull(document.coverImagePath) +
                document.pages.mapNotNull { page -> when (val background = page.background) { is PageBackground.Pdf -> background.assetPath; is PageBackground.Image -> background.assetPath; else -> null } } +
                document.pages.flatMap { it.savedSelections }.map { it.surface.assetPath } +
                document.pages.flatMap(InkPage::layers).mapNotNull { it.mask?.surface?.assetPath } +
                document.pages.flatMap { it.elements }.mapNotNull { element -> when (element) { is PageElement.Image -> element.assetPath; is PageElement.Fill -> element.maskAssetPath; is PageElement.Shape -> element.maskAssetPath; else -> null } }
            writeDistinctFiles(zip, "assets", assets)
            writeRasterTileFiles(
                zip,
                document.pages.flatMap(InkPage::layers).mapNotNull(InkLayer::rasterTileSurface),
            )
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
                    require(normalized == "manifest.json" || normalized.startsWith("assets/") || normalized.startsWith("brush-assets/") || normalized.startsWith("brush-sources/") || normalized.startsWith("pixel-tiles/")) { "Bundle contains an unsupported entry" }
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
            val decoded = DocumentCheckpointCodec.decode(manifest.readText())
            fun retained(relative: String, brush: Boolean = false): String {
                val normalized = relative.replace('\\', '/')
                val expectedPrefix = if (brush) "brush-assets/" else "assets/"
                require(normalized.startsWith(expectedPrefix) && !normalized.startsWith('/') && normalized.split('/').none { it == ".." }) { "Manifest contains an unsafe asset path" }
                val source = File(temporaryRoot, normalized)
                require(source.canonicalPath.startsWith(temporaryRoot.canonicalPath + File.separator) && source.isFile) { "Bundle is missing $normalized" }
                return retainFile(source, if (brush) brushAssetDirectory else directory).absolutePath
            }
            fun retainedRaster(reference: RasterTileSurfaceRef?): RasterTileSurfaceRef? {
                if (reference == null) return null
                val relative = reference.manifestAssetPath.replace('\\', '/')
                require(relative.startsWith("pixel-tiles/manifests/") &&
                    relative.split('/').none { it == ".." }
                ) { "Manifest contains an unsafe raster tile path" }
                val sourceManifest = File(temporaryRoot, relative)
                require(sourceManifest.canonicalPath.startsWith(temporaryRoot.canonicalPath + File.separator) &&
                    sourceManifest.isFile
                ) { "Bundle is missing $relative" }
                val importedManifest = assetLifecycle.importRasterTileManifest(sourceManifest.readText()) { hash ->
                    File(temporaryRoot, "pixel-tiles/blobs/$hash.tile")
                        .takeIf(File::isFile)?.readBytes()
                }
                val metadata = pixelTileStore.inspect(importedManifest)
                require(metadata.surfaceId == reference.surfaceId &&
                    metadata.logicalWidth == reference.logicalWidth &&
                    metadata.logicalHeight == reference.logicalHeight &&
                    metadata.tileSize == reference.tileSize &&
                    metadata.revision == reference.revision
                ) { "Bundle raster tile reference does not match its verified manifest" }
                return reference.copy(manifestAssetPath = importedManifest.absolutePath)
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
                    savedSelections = page.savedSelections.map { it.copy(surface = it.surface.copy(assetPath = retained(it.surface.assetPath))) },
                    layers = page.layers.map { layer -> layer.copy(
                        rasterTileSurface = retainedRaster(layer.rasterTileSurface),
                        mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                            reference.copy(assetPath = retained(reference.assetPath))
                        }),
                    ) },
                    elements = page.elements.map { element -> when (element) {
                        is PageElement.Image -> element.copy(assetPath = retained(element.assetPath))
                        is PageElement.Fill -> element.copy(maskAssetPath = retained(element.maskAssetPath))
                        is PageElement.Shape -> element.copy(maskAssetPath = element.maskAssetPath?.let { retained(it) })
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

    private fun writeRasterTileFiles(zip: ZipOutputStream, references: List<RasterTileSurfaceRef>) {
        references.flatMap(::referencedRasterTileFiles)
            .distinctBy { it.canonicalPath }
            .sortedBy { it.name }
            .forEach { file ->
                val kind = when (file.parentFile?.name) {
                    "manifests" -> "manifests"
                    "blobs" -> "blobs"
                    else -> error("Raster tile asset escaped its store")
                }
                zip.putNextEntry(ZipEntry("pixel-tiles/$kind/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
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
        includeRaster: Boolean = true,
        applyLayerEffects: Boolean = true,
        includeDraftLayers: Boolean = false,
    ) {
        val page = PageCropTransform.project(sourcePage)
        val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
        val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.toInt() ?: Color.WHITE
        if (includeBackground) {
            canvas.drawColor(if (backgroundVisible) paperColor else Color.WHITE)
            if (backgroundVisible) {
                val frame = page.backgroundFrame
                val backgroundWidth = if (frame == null) width else (frame.width * width / page.width).toInt().coerceIn(1, 8192)
                renderBackground(sourcePage.background, backgroundWidth, sourcePage.backgroundRotationQuarterTurns,
                    sourcePage.backgroundSourceCrop ?: sourcePage.crop)?.let { bitmap ->
                    val target = if (frame == null) RectF(0f, 0f, width.toFloat(), height.toFloat()) else
                        RectF(frame.left * width / page.width, frame.top * height / page.height,
                            (frame.left + frame.width) * width / page.width, (frame.top + frame.height) * height / page.height)
                    canvas.drawBitmap(bitmap, null, target, Paint(Paint.FILTER_BITMAP_FLAG)); bitmap.recycle()
                }
            }
            if (backgroundVisible) (page.background as? PageBackground.Paper)?.let { PaperPatternRenderer.draw(canvas, it.pattern, it.density, width.toFloat(), height.toFloat()) }
        } else canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val visibleLayers = page.layers.filter { LayerHierarchy.effectiveVisible(page.layers, it) && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) && (includeDraftLayers || !it.draft) && (includedLayerIds == null || it.id in includedLayerIds) }
        if (drawV5RasterProjectionIfCompatible(
                canvas = canvas,
                sourcePage = sourcePage,
                projectedPage = page,
                visibleLayers = visibleLayers,
                width = width,
                height = height,
                includedLayerIds = includedLayerIds,
                includedContentIds = includedContentIds,
                includeRaster = includeRaster,
                applyLayerEffects = applyLayerEffects,
            )) return
        lateinit var drawNode: (LayerCompositingNode, LayerCompositingNode?) -> Unit
        fun drawLeaf(layer: InkLayer, lowerNode: LayerCompositingNode?) {
            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                blendMode = if (applyLayerEffects) layer.blendMode.toBlendMode() else android.graphics.BlendMode.SRC_OVER
                colorFilter = layer.colorReplacement?.toColorFilter()
            }
            // A transparent layer would hide a destination-dependent brush mode from the page.
            // Bypass it only for those brushes on an otherwise direct layer; ordinary brushes keep
            // the established export surface so their antialiasing remains pixel-identical live.
            val hasLayerEffect = layer.opacity < .999f || layer.blendMode != LayerBlendMode.NORMAL ||
                layer.mask?.enabled == true || layer.clippedToBelow || layer.colorReplacement != null
            val requiresLayerIsolation = applyLayerEffects && hasLayerEffect
            val layerCheckpoint = if (requiresLayerIsolation) canvas.saveLayer(null, layerPaint) else canvas.save()
            fun contentAllowed(id: String, contentIds: Set<String>?): Boolean =
                (includedContentIds == null || id in includedContentIds) && (contentIds == null || id in contentIds)
            fun drawLayerItems(targetLayerId: String, contentIds: Set<String>? = null) {
            page.elements.filter { it.layerId == targetLayerId && contentAllowed(it.id, contentIds) }.forEach { element ->
            if (element.deformation != null) {
                drawDeformedElement(canvas, element, page.width, page.height, width, height)
                return@forEach
            }
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
                                val checkpoint = canvas.saveLayer(left, top, right, bottom, null)
                                if (drawFillMask(
                                    canvas,
                                    path,
                                    android.graphics.RectF(left, top, right, bottom),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                                )) {
                                    canvas.saveLayer(left, top, right, bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        blendMode = android.graphics.BlendMode.SRC_IN
                                    })
                                    checkpoint
                                } else {
                                    canvas.restoreToCount(checkpoint)
                                    null
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
                is PageElement.Image -> {
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
                    try { ImageAssetDecoder.drawCropped(canvas, element.assetPath, element.crop, destination, paint) } finally { canvas.restore() }
                }
                is PageElement.Fill -> {
                    val destination = android.graphics.RectF(left, top, right, bottom)
                    val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    colorPaint.colorFilter = PorterDuffColorFilter(element.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
                    colorPaint.alpha = (element.opacity.coerceIn(0f, 1f) * 255).toInt()
                    drawFillMask(canvas, element.maskAssetPath, destination, colorPaint)
                }
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
                if (!includeRaster || !targetLayer.isPaintSurfaceLayer()) return
                targetLayer.rasterTileSurface?.let { reference ->
                    val bitmap = renderRasterTileReference(
                        reference,
                        targetLayer.rasterRotationQuarterTurns,
                        sourcePage.crop,
                        width,
                        height,
                    )
                    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                    return
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
                val currentIds = page.elements.filter { it.layerId == targetLayer.id }.mapTo(mutableSetOf(), PageElement::id)
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
            if (applyLayerEffects) layer.mask?.takeIf { it.enabled }?.surface?.let { reference ->
                val sparse = runCatching { layer.mask!!.expressed(loadMaskTileSurface(reference).snapshot()) }.getOrNull()
                    ?: return@let
                val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    blendMode = android.graphics.BlendMode.DST_IN
                })
                app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(
                    sparse,
                    canvas,
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                )
                canvas.restoreToCount(maskCheckpoint)
            }
            if (applyLayerEffects && layer.clippedToBelow) {
                val lowerLayer = lowerNode?.layer
                if (lowerLayer == null) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                } else {
                    val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    if (applyLayerEffects && includedLayerIds == null) drawNode(requireNotNull(lowerNode), null)
                    else drawLayerWithAlphaLock(lowerLayer)
                    canvas.restoreToCount(maskCheckpoint)
                }
            }
            canvas.restoreToCount(layerCheckpoint)
        }
        fun drawSiblings(nodes: List<LayerCompositingNode>) {
            var previous: LayerCompositingNode? = null
            nodes.forEach { node ->
                drawNode(node, previous)
                if (!node.layer.clippedToBelow) previous = node
            }
        }
        val visibleIds = visibleLayers.mapTo(hashSetOf()) { it.id }
        drawNode = draw@{ node, lower ->
            val layer = node.layer
            if (!layer.visible) return@draw
            if (layer.role != LayerRole.FOLDER) {
                if (layer.id in visibleIds) drawLeaf(layer, lower)
            } else {
                val save = canvas.saveLayer(null, Paint().apply {
                    alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
                    blendMode = if (applyLayerEffects) layer.blendMode.toBlendMode() else android.graphics.BlendMode.SRC_OVER
                })
                drawSiblings(node.children)
                layer.mask?.takeIf { it.enabled }?.surface?.let { reference ->
                    val mask = layer.mask!!.expressed(loadMaskTileSurface(reference).snapshot())
                    val checkpoint = canvas.saveLayer(null, Paint().apply { blendMode = android.graphics.BlendMode.DST_IN })
                    app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(mask, canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
                    canvas.restoreToCount(checkpoint)
                }
                if (layer.clippedToBelow) {
                    val checkpoint = canvas.saveLayer(null, Paint().apply { blendMode = android.graphics.BlendMode.DST_IN })
                    if (lower != null) drawNode(lower, null)
                    canvas.restoreToCount(checkpoint)
                }
                canvas.restoreToCount(save)
            }
        }
        // Loading one editable surface must never bake in its parent's opacity or mask.
        if (applyLayerEffects && includedLayerIds == null) drawSiblings(LayerCompositingTree.roots(page.layers))
        else visibleLayers.forEachIndexed { index, layer -> drawLeaf(layer, visibleLayers.getOrNull(index - 1)?.let(::LayerCompositingNode)) }
    }

    /** Rerender editable source, then present the saved lattice; no document raster replacement. */
    private fun drawDeformedElement(canvas: Canvas, element: PageElement, pageWidth: Float,
                                   pageHeight: Float, width: Int, height: Int) {
        val warp = requireNotNull(element.deformation)
        fun original(): PageElement = when (element) {
            is PageElement.Text -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
            is PageElement.Sticky -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
            is PageElement.Shape -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
            is PageElement.Image -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
            is PageElement.Fill -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
            is PageElement.Link -> element.copy(x = 0f, y = 0f, rotationDegrees = 0f, deformation = null)
        }
        val source = InkPage(width = element.width, height = element.height,
            layers = listOf(InkLayer(id = element.layerId, name = "Source")), elements = listOf(original()))
        val nativeWidth = kotlin.math.ceil(element.width * width / pageWidth).toInt().coerceIn(1, 4096)
        val bitmap = renderElementLayerBitmap(source, element.layerId, nativeWidth)
        try {
            val base = element.withDeformation(null)
            val vertices = FloatArray(warp.vertices.size * 2)
            warp.vertices.forEachIndexed { i, uv ->
                val point = base.contentPoint(uv.x, uv.y, pageWidth, pageHeight)
                vertices[i * 2] = point.x * width / pageWidth
                vertices[i * 2 + 1] = point.y * height / pageHeight
            }
            canvas.drawBitmapMesh(bitmap, warp.columns - 1, warp.rows - 1, vertices, 0, null, 0,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally { bitmap.recycle() }
    }

    /** Export counterpart of the live V5 sparse-tile projection. */
    private fun drawV5RasterProjectionIfCompatible(
        canvas: Canvas,
        sourcePage: InkPage,
        projectedPage: InkPage,
        visibleLayers: List<InkLayer>,
        width: Int,
        height: Int,
        includedLayerIds: Set<String>?,
        includedContentIds: Set<String>?,
        includeRaster: Boolean,
        applyLayerEffects: Boolean,
    ): Boolean {
        if (!includeRaster || !applyLayerEffects) return false
        if (includedLayerIds != null || includedContentIds != null) return false
        val through = projectedPage.layers.any { it.role == LayerRole.FOLDER && it.passThrough }
        val hasAdjustments = projectedPage.layers.any {
            it.role == LayerRole.ADJUSTMENT && it.visible && it.adjustment != null
        }
        val hasElements = projectedPage.elements.any { element -> visibleLayers.any { it.id == element.layerId } }
        val customBlend = projectedPage.layers.any { it.blendMode.requiresPixelCompositor }
        if (hasElements && !through && !hasAdjustments && !customBlend) return false
        val rasterLayers = visibleLayers.filter { layer ->
            layer.isPaintSurfaceLayer() && layer.rasterTileSurface != null
        }
        if (rasterLayers.isEmpty() && (!hasElements || (!through && !hasAdjustments && !customBlend))) return false
        if (rasterLayers.any { it.mask?.enabled == true && it.mask.surface == null }) return false

        val surfaces = rasterLayers.map { layer ->
            layer to renderRasterLayerSurface(sourcePage, layer.id, width)
        }
        val first = surfaces.firstOrNull()?.second ?: PixelTileSurface(width, height)
        if (surfaces.any { (_, surface) ->
                surface.logicalWidth != first.logicalWidth || surface.logicalHeight != first.logicalHeight ||
                    surface.tileSize != first.tileSize || surface.pixelFormat != first.pixelFormat
            }) return false
        val projection = PixelGroupProjectionCache(
            width = first.logicalWidth,
            height = first.logicalHeight,
            tileSize = first.tileSize,
            format = first.pixelFormat,
            surfaceId = "export-raster-projection-${sourcePage.id}-$width",
        )
        val rasterInputs = surfaces.associate { (layer, surface) -> layer.id to PixelLayerProjectionInput(layer.id, surface.snapshot()) }
        val contentCache = if (hasElements) LayerContentProjectionCache(first.logicalWidth, first.logicalHeight, first.tileSize, first.pixelFormat) else null
        contentCache?.beginFrame()
        val inputs = try { visibleLayers.associate { layer ->
            val raw = rasterInputs[layer.id]
            val ids = projectedPage.elements.filter { it.layerId == layer.id }.mapTo(hashSetOf()) { it.id }
            val captured = layer.alphaLockBaseContentIds.toSet().takeIf { layer.alphaLock && it.isNotEmpty() }
            val base = if (hasElements && ids.isNotEmpty()) renderElementLayerBitmap(sourcePage, layer.id, width, captured) else null
            val additions = if (hasElements && captured != null && (ids - captured).isNotEmpty())
                renderElementLayerBitmap(sourcePage, layer.id, width, ids - captured) else null
            val composed = try { contentCache?.resolve(layer.id, raw, base, additions, captured != null) }
                finally { base?.recycle(); additions?.recycle() }
            val content = if (composed != null) PixelLayerProjectionInput(layer.id, composed)
                else raw ?: PixelLayerProjectionInput(layer.id, projection.emptySnapshot)
            layer.id to content.copy(opacity = layer.opacity, blendMode = layer.blendMode.toPixelBlendMode(),
                colorReplacement = layer.colorReplacement,
                mask = layer.mask?.takeIf(LayerMask::enabled)?.let { mask ->
                    mask.surface?.let { mask.expressed(loadMaskTileSurface(it).snapshot()) } },
                clipToBaseBelow = layer.clippedToBelow)
        } } finally { contentCache?.endFrame() }
        val visibleIds = visibleLayers.mapTo(hashSetOf()) { it.id }
        val nodes = LayerCompositingTree.project<PixelProjectionNode>(projectedPage.layers,
            paint = { layer -> if (layer.role == LayerRole.ADJUSTMENT && layer.adjustment != null)
                PixelProjectionNode.Adjustment(layer.id, layer.adjustment, layer.opacity, layer.blendMode.toPixelBlendMode(),
                    layer.mask?.takeIf { it.enabled }?.let { mask ->
                        mask.surface?.let { mask.expressed(loadMaskTileSurface(it).snapshot()) } },
                    layer.id in visibleIds)
            else PixelProjectionNode.Paint((inputs[layer.id] ?: PixelLayerProjectionInput(layer.id,
                projection.emptySnapshot, opacity = layer.opacity, clipToBaseBelow = layer.clippedToBelow))
                .copy(visible = layer.id in visibleIds)) },
            group = { layer, children -> PixelProjectionNode.Group(layer.id, children, layer.opacity,
                layer.blendMode.toPixelBlendMode(),
                layer.mask?.takeIf { it.enabled }?.let { mask ->
                    mask.surface?.let { mask.expressed(loadMaskTileSurface(it).snapshot()) } },
                layer.clippedToBelow, visible = layer.visible, passThrough = layer.passThrough) })
        val update = projection.update(nodes)
        PixelTileCanvasRenderer().use { renderer ->
            renderer.draw(canvas, update.snapshot, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        }
        v5RasterProjectionExportCount++
        return true
    }

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

    private fun midpoint(start: StrokePoint, end: StrokePoint) = StrokePoint(
        x = (start.x + end.x) / 2f, y = (start.y + end.y) / 2f,
        pressure = (start.pressure + end.pressure) / 2f,
        timeMs = (start.timeMs + end.timeMs) / 2L,
        tilt = (start.tilt + end.tilt) / 2f,
        orientation = (start.orientation + end.orientation) / 2f,
        velocity = (start.velocity + end.velocity) / 2f,
        pathDistance = (start.pathDistance + end.pathDistance) / 2f,
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
        const val DUAL_GRAIN_SEED = 0x4D2A61C7
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

    private fun copyAsset(uri: Uri, extension: String, checkCancelled: () -> Unit = {}): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val temporary = File(directory, ".${System.nanoTime()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            } } ?: error("Selected asset is unreadable")
            checkCancelled()
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "$hash.${extension.lowercase().filter(Char::isLetterOrDigit).ifBlank { "asset" }}")
            if (target.exists()) temporary.delete() else check(temporary.renameTo(target)) { "Could not retain imported asset" }
            return target
        } finally { temporary.delete() }
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
