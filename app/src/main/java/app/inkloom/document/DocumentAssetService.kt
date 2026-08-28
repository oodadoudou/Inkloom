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
import app.inkloom.engine.v5.composite.PixelLayerProjectionCache
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

class DocumentAssetService(private val context: Context) {
    private val directory = File(context.filesDir, "document-assets").apply { mkdirs() }
    private val pixelTileDirectory = File(directory, "pixel-tiles")
    private val pixelTileStore = PixelTileArchiveStore(pixelTileDirectory)
    private val brushAssetDirectory = File(context.filesDir, "brush-assets").apply { mkdirs() }
    private val assetLifecycleLock = Any()
    private val pendingDocumentAssets = linkedSetOf<String>()
    private val pendingRasterTileManifests = linkedSetOf<String>()
    @androidx.annotation.VisibleForTesting
    internal var v5RasterProjectionExportCount: Int = 0

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

    /** Persists a selection-derived mask without materializing a page-sized Bitmap. */
    fun saveFillMask(mask: MaskTileSurfaceSnapshot): String =
        saveDocumentBytes(MaskTileArchiveCodec.encode(mask), "fill-mask.v3")

    fun saveMaskTileSurface(mask: MaskTileSurfaceSnapshot): MaskTileSurfaceRef {
        val path = saveDocumentBytes(MaskTileArchiveCodec.encode(mask), "layer-mask.v3")
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
    ): RasterTileSurfaceRef = synchronized(assetLifecycleLock) {
        val manifest = pixelTileStore.save(
            surface.snapshot(),
            previousReference?.manifestAssetPath?.let(::File),
        )
        val metadata = pixelTileStore.inspect(manifest)
        pendingRasterTileManifests += manifest.canonicalPath
        RasterTileSurfaceRef(
            manifestAssetPath = manifest.absolutePath,
            surfaceId = metadata.surfaceId,
            logicalWidth = metadata.logicalWidth,
            logicalHeight = metadata.logicalHeight,
            tileSize = metadata.tileSize,
            revision = metadata.revision,
            pixelFormat = metadata.pixelFormat.manifestId,
        )
    }

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

    private fun saveDocumentBytes(bytes: ByteArray, kind: String): String {
        require(bytes.isNotEmpty()) { "Could not encode $kind" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val target = File(directory, "$hash.$kind")
        val temporary = File(directory, ".${System.nanoTime()}.tmp")
        try {
            if (!target.exists()) {
                temporary.outputStream().use { output -> output.write(bytes); output.flush() }
                check(temporary.renameTo(target)) { "Could not retain $kind" }
            }
            synchronized(assetLifecycleLock) { pendingDocumentAssets += target.canonicalPath }
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

    fun releaseRasterTileReservation(reference: RasterTileSurfaceRef?) {
        if (reference == null) return
        synchronized(assetLifecycleLock) {
            runCatching { File(reference.manifestAssetPath).canonicalPath }.getOrNull()
                ?.let(pendingRasterTileManifests::remove)
        }
    }

    /** Loads a fill mask PNG, or null if it cannot be decoded. */
    fun loadFillMask(maskAssetPath: String): Bitmap? = runCatching { BitmapFactory.decodeFile(maskAssetPath) }.getOrNull()

    fun loadSparseFillMask(maskAssetPath: String): MaskTileSurface? = runCatching {
        val bytes = File(maskAssetPath).readBytes()
        if (MaskTileArchiveCodec.isSparseMask(bytes)) MaskTileArchiveCodec.decode(bytes) else null
    }.getOrNull()

    /** Draws either a V3 sparse mask or a read-only legacy PNG without changing document truth. */
    fun drawFillMask(canvas: Canvas, maskAssetPath: String, destination: RectF, paint: Paint): Boolean {
        loadSparseFillMask(maskAssetPath)?.let { surface ->
            val mask = SparseAlphaMask.fromSurface(surface) ?: return false
            mask.draw(canvas, destination, paint)
            return true
        }
        val bitmap = loadFillMask(maskAssetPath) ?: return false
        return try {
            canvas.drawBitmap(bitmap, null, destination, paint)
            true
        } finally {
            bitmap.recycle()
        }
    }

    /** Removes only private document assets that are no longer referenced by a document or template. */
    fun garbageCollectUnreferencedAssets(
        documents: List<InkDocument>,
        pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
    ): Int = synchronized(assetLifecycleLock) {
        val legacyRemoved = DocumentAssetGarbageCollector.collect(
            directory,
            documents,
            pageTemplates,
            pendingDocumentAssets.toSet(),
        )
        val retainedManifests = documents.flatMap { document ->
            document.pages.flatMap { page ->
                page.layers.mapNotNull { layer -> layer.rasterTileSurface?.manifestAssetPath }
            }
        }.map(::File) + pendingRasterTileManifests.map(::File)
        val tileResult = pixelTileStore.garbageCollect(retainedManifests)
        legacyRemoved + tileResult.manifestsDeleted + tileResult.blobsDeleted
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
        val sourceWidth = if (turns % 2 == 0) targetWidth else targetHeight
        val sourceHeight = if (turns % 2 == 0) targetHeight else targetWidth
        val materialized = PixelTileBitmapAdapter.render(
            snapshot,
            source = sourceRect,
            targetWidth = sourceWidth,
            targetHeight = sourceHeight,
        )
        if (turns == 0) return materialized
        return requireNotNull(rotateQuarterTurns(materialized, turns))
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
                    .toInt().coerceIn(visibleSourceWidth, 4096)
                val rotatedHeight = (visibleSourceHeight / (crop.bottom - crop.top).coerceAtLeast(.01f))
                    .toInt().coerceIn(visibleSourceHeight, 4096)
                PixelTileSurface(
                    logicalWidth = if (turns % 2 == 0) rotatedWidth else rotatedHeight,
                    logicalHeight = if (turns % 2 == 0) rotatedHeight else rotatedWidth,
                )
        }
        val rotatedWidth = if (turns % 2 == 0) surface.logicalWidth else surface.logicalHeight
        val rotatedHeight = if (turns % 2 == 0) surface.logicalHeight else surface.logicalWidth
        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        val normalizedLeft = crop.left + patchLeft.toFloat() / visibleSourceWidth * cropWidth
        val normalizedTop = crop.top + patchTop.toFloat() / visibleSourceHeight * cropHeight
        val normalizedRight = crop.left + (patchLeft + patch.width).toFloat() / visibleSourceWidth * cropWidth
        val normalizedBottom = crop.top + (patchTop + patch.height).toFloat() / visibleSourceHeight * cropHeight
        val destination = PixelRect(
            kotlin.math.floor(normalizedLeft * rotatedWidth).toInt().coerceIn(0, rotatedWidth - 1),
            kotlin.math.floor(normalizedTop * rotatedHeight).toInt().coerceIn(0, rotatedHeight - 1),
            kotlin.math.ceil(normalizedRight * rotatedWidth).toInt().coerceIn(1, rotatedWidth),
            kotlin.math.ceil(normalizedBottom * rotatedHeight).toInt().coerceIn(1, rotatedHeight),
        )
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
                layers = page.layers.map { layer -> layer.copy(
                    rasterTileSurface = portableRaster(layer.rasterTileSurface),
                    mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                        reference.copy(assetPath = "assets/${File(reference.assetPath).name}")
                    }),
                ) },
                elements = page.elements.map { element -> when (element) {
                    is PageElement.Image -> element.copy(assetPath = "assets/${File(element.assetPath).name}")
                    is PageElement.AudioMarker -> element.copy(assetPath = "assets/${File(element.assetPath).name}")
                    is PageElement.Fill -> element.copy(maskAssetPath = "assets/${File(element.maskAssetPath).name}")
                    is PageElement.Shape -> element.copy(maskAssetPath = element.maskAssetPath?.let { "assets/${File(it).name}" })
                    else -> element
                } },
                ) },
            )
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(DocumentCheckpointCodec.encode(portable).encodeToByteArray()); zip.closeEntry()
            val assets = listOfNotNull(document.coverImagePath) +
                document.pages.mapNotNull { page -> when (val background = page.background) { is PageBackground.Pdf -> background.assetPath; is PageBackground.Image -> background.assetPath; else -> null } } +
                document.pages.flatMap(InkPage::layers).mapNotNull { it.mask?.surface?.assetPath } +
                document.pages.flatMap { it.elements }.mapNotNull { element -> when (element) { is PageElement.Image -> element.assetPath; is PageElement.AudioMarker -> element.assetPath; is PageElement.Fill -> element.maskAssetPath; is PageElement.Shape -> element.maskAssetPath; else -> null } }
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
                val importedManifest = synchronized(assetLifecycleLock) {
                    pixelTileStore.import(sourceManifest.readText()) { hash ->
                        File(temporaryRoot, "pixel-tiles/blobs/$hash.tile")
                            .takeIf(File::isFile)?.readBytes()
                    }.also { pendingRasterTileManifests += it.canonicalPath }
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
                    layers = page.layers.map { layer -> layer.copy(
                        rasterTileSurface = retainedRaster(layer.rasterTileSurface),
                        mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                            reference.copy(assetPath = retained(reference.assetPath))
                        }),
                    ) },
                    elements = page.elements.map { element -> when (element) {
                        is PageElement.Image -> element.copy(assetPath = retained(element.assetPath))
                        is PageElement.AudioMarker -> element.copy(assetPath = retained(element.assetPath))
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
            if (backgroundVisible) renderBackground(sourcePage.background, width, sourcePage.backgroundRotationQuarterTurns, sourcePage.crop)?.let { bitmap -> canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), null); bitmap.recycle() }
            if (backgroundVisible) (page.background as? PageBackground.Paper)?.let { drawPaperPattern(canvas, it.pattern, width, height) }
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
        visibleLayers.forEachIndexed { layerIndex, layer ->
            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                blendMode = layer.blendMode.toBlendMode()
            }
            // A transparent layer would hide a destination-dependent brush mode from the page.
            // Bypass it only for those brushes on an otherwise direct layer; ordinary brushes keep
            // the established export surface so their antialiasing remains pixel-identical live.
            val hasLayerEffect = layer.opacity < .999f || layer.blendMode != LayerBlendMode.NORMAL ||
                layer.mask?.enabled == true || layer.clippedToBelow
            val requiresLayerIsolation = applyLayerEffects && hasLayerEffect
            val layerCheckpoint = if (requiresLayerIsolation) canvas.saveLayer(null, layerPaint) else canvas.save()
            fun contentAllowed(id: String, contentIds: Set<String>?): Boolean =
                (includedContentIds == null || id in includedContentIds) && (contentIds == null || id in contentIds)
            fun drawLayerItems(targetLayerId: String, contentIds: Set<String>? = null) {
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
                is PageElement.Fill -> {
                    val destination = android.graphics.RectF(left, top, right, bottom)
                    val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    colorPaint.colorFilter = PorterDuffColorFilter(element.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
                    colorPaint.alpha = (element.opacity.coerceIn(0f, 1f) * 255).toInt()
                    drawFillMask(canvas, element.maskAssetPath, destination, colorPaint)
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
                val sparse = runCatching { loadMaskTileSurface(reference).snapshot() }.getOrNull()
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
        if (projectedPage.elements.any { element -> visibleLayers.any { it.id == element.layerId } }) return false
        val rasterLayers = visibleLayers.filter { layer ->
            layer.isPaintSurfaceLayer() && layer.rasterTileSurface != null
        }
        if (rasterLayers.isEmpty()) return false
        if (rasterLayers.any { it.mask?.enabled == true && it.mask.surface == null }) return false

        val surfaces = rasterLayers.map { layer ->
            layer to renderRasterLayerSurface(sourcePage, layer.id, width)
        }
        val first = surfaces.first().second
        if (surfaces.any { (_, surface) ->
                surface.logicalWidth != first.logicalWidth || surface.logicalHeight != first.logicalHeight ||
                    surface.tileSize != first.tileSize || surface.pixelFormat != first.pixelFormat
            }) return false
        val projection = PixelLayerProjectionCache(
            logicalWidth = first.logicalWidth,
            logicalHeight = first.logicalHeight,
            tileSize = first.tileSize,
            pixelFormat = first.pixelFormat,
            surfaceId = "export-raster-projection-${sourcePage.id}-$width",
        )
        val update = projection.update(surfaces.map { (layer, surface) ->
            PixelLayerProjectionInput(
                layerId = layer.id,
                pixels = surface.snapshot(),
                opacity = layer.opacity,
                blendMode = layer.blendMode.toPixelBlendMode(),
                mask = layer.mask?.takeIf(LayerMask::enabled)?.surface?.let { loadMaskTileSurface(it).snapshot() },
                clipToBaseBelow = layer.clippedToBelow,
            )
        })
        PixelTileCanvasRenderer().use { renderer ->
            renderer.draw(canvas, update.snapshot, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        }
        v5RasterProjectionExportCount++
        return true
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
