package app.inkloom.document

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Xml
import app.inkloom.engine.v5.surface.*
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import java.io.*
import java.util.zip.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser

/** File interchange lives outside document storage; imports are fully prepared before publication. */
internal class RasterFormatService(private val context: Context, private val assets: DocumentAssetService) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val oraModes = mapOf(LayerBlendMode.NORMAL to "svg:src-over", LayerBlendMode.MULTIPLY to "svg:multiply",
        LayerBlendMode.SCREEN to "svg:screen", LayerBlendMode.ADD to "svg:plus", LayerBlendMode.OVERLAY to "svg:overlay",
        LayerBlendMode.DARKEN to "svg:darken", LayerBlendMode.LIGHTEN to "svg:lighten", LayerBlendMode.COLOR_DODGE to "svg:color-dodge",
        LayerBlendMode.COLOR_BURN to "svg:color-burn", LayerBlendMode.HARD_LIGHT to "svg:hard-light", LayerBlendMode.SOFT_LIGHT to "svg:soft-light",
        LayerBlendMode.DIFFERENCE to "svg:difference", LayerBlendMode.HUE to "svg:hue", LayerBlendMode.SATURATION to "svg:saturation",
        LayerBlendMode.COLOR to "svg:color", LayerBlendMode.LUMINOSITY to "svg:luminosity")
    private val psdModes = mapOf(LayerBlendMode.NORMAL to "norm", LayerBlendMode.MULTIPLY to "mul ", LayerBlendMode.SCREEN to "scrn",
        LayerBlendMode.ADD to "lddg", LayerBlendMode.OVERLAY to "over", LayerBlendMode.DARKEN to "dark", LayerBlendMode.LIGHTEN to "lite",
        LayerBlendMode.COLOR_DODGE to "div ", LayerBlendMode.COLOR_BURN to "idiv", LayerBlendMode.HARD_LIGHT to "hLit",
        LayerBlendMode.SOFT_LIGHT to "sLit", LayerBlendMode.DIFFERENCE to "diff", LayerBlendMode.EXCLUSION to "smud", LayerBlendMode.HUE to "hue ",
        LayerBlendMode.SATURATION to "sat ", LayerBlendMode.COLOR to "colr", LayerBlendMode.LUMINOSITY to "lum ", LayerBlendMode.LINEAR_BURN to "lbrn",
        LayerBlendMode.SUBTRACT to "fsub", LayerBlendMode.DIVIDE to "fdiv", LayerBlendMode.VIVID_LIGHT to "vLit", LayerBlendMode.LINEAR_LIGHT to "lLit",
        LayerBlendMode.PIN_LIGHT to "pLit", LayerBlendMode.HARD_MIX to "hMix", LayerBlendMode.DARKER_COLOR to "dkCl", LayerBlendMode.LIGHTER_COLOR to "lgCl")
    @Serializable private data class Stack(val version: Int = 1, val width: Int, val height: Int, val dpi: Float,
        val layers: List<InkLayer>, val files: Map<String, String>, val masks: Map<String, String>)

    fun export(page: InkPage, uri: Uri, options: PageRasterExportOptions) {
        if(options.format == RasterFileFormat.PNG) { assets.exportPagePng(page, uri, options); return }
        val width = options.width ?: PagePngExportSize.from(page).width
        val height = options.height ?: PagePngExportSize.from(page).height
        require(width in 1..8192 && height in 1..8192)
        if(options.format in setOf(RasterFileFormat.OPEN_RASTER, RasterFileFormat.PSD, RasterFileFormat.LAYERED_PNG)) {
            require(page.colorManagement.assignedProfile==null && page.colorManagement.outputProfile==null && page.colorManagement.outputSpace==app.inkloom.color.RgbOutputSpace.SRGB) { "Profile-assigned artwork requires a native Inkloom bundle or flattened PNG export" }
            exportStack(page, uri, options, width, height); return
        }
        if(options.format==RasterFileFormat.CMYK_TIFF) {
            val profile=page.colorManagement.cmykProfile?.let(app.inkloom.color.IccColorPipeline::profile)
                ?: error("Select a CMYK printer profile in Color management before CMYK export")
            require(app.inkloom.color.NativeColorManagement.profileKind(profile)==2) { "CMYK output requires a CMYK ICC profile" }
            app.inkloom.color.IccColorPipeline(page.colorManagement,cmykProfile=profile).use { pipeline ->
                assets.withPageLinearRows(page,width,height,true) { row ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        TiffRasterCodec.writeCmyk(output,width,height,page.dpi,profile) { y,bytes ->
                            val pixels=row(y)
                            for(x in 0 until width) { val a=pixels[x*4+3];for(c in 0..2)pixels[x*4+c]=pixels[x*4+c]*a+1-a;pixels[x*4+3]=1f }
                            pipeline.convert(pixels,cmyk=bytes)
                        }
                    } ?: error("Could not open export destination")
                }
            }; return
        }
        require(page.colorManagement.outputProfile==null && page.colorManagement.outputSpace==app.inkloom.color.RgbOutputSpace.SRGB) {
            "Use PNG or CMYK TIFF for profile-controlled output; other formats currently require sRGB output"
        }
        val temporary = File.createTempFile("raster-composite-", ".png", context.cacheDir)
        try {
            assets.exportPagePng(page, Uri.fromFile(temporary), options.copy(format = RasterFileFormat.PNG,
                includeBackground = options.includeBackground || options.format == RasterFileFormat.JPEG))
            context.contentResolver.openOutputStream(uri)?.use { output -> when(options.format) {
                RasterFileFormat.JPEG -> {
                    require(width.toLong()*height <= 16_777_216L) { "JPEG currently supports up to 16 megapixels; use PNG/TIFF for larger pages" }
                    val bitmap = BitmapFactory.decodeFile(temporary.path) ?: error("Could not decode export composite")
                    try { check(bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality.coerceIn(1,100), output)) } finally { bitmap.recycle() }
                }
                RasterFileFormat.TIFF -> withPngRows(temporary) { row -> TiffRasterCodec.write(output, width, height, page.dpi, row) }
                else -> error("Unsupported export format")
            } } ?: error("Could not open export destination")
        } finally { temporary.delete() }
    }

    fun import(uri: Uri, name: String): InkDocument {
        val extension = name.substringAfterLast('.').lowercase()
        val temporary = File.createTempFile("artwork-import-", ".$extension", context.cacheDir)
        val reservations = mutableListOf<RasterTileSurfaceRef>(); val masks = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(64*1024); var count = 0L
                while(true) { val n = input.read(buffer); if(n < 0) break; count += n; require(count <= 1_073_741_824L) { "Artwork exceeds 1 GB import limit" }; output.write(buffer,0,n) }
            } } ?: error("Unreadable artwork")
            fun save(surface: PixelTileSurface) = assets.saveRasterTileSurface(surface).also(reservations::add)
            val pages = when(extension) {
                "tif", "tiff" -> TiffRasterCodec.read(temporary).map { image ->
                    val ref = save(surface(image.width, image.height, image.pixels))
                    InkPage(width=image.width.toFloat(), height=image.height.toFloat(), dpi=image.dpi,
                        layers=listOf(InkLayer(name="Image", rasterTileSurface=ref)))
                }
                "psd" -> listOf(importPsd(PsdRasterCodec.read(temporary), ::save, masks))
                "ora", "zip" -> listOf(importStack(temporary, ::save, masks))
                else -> error("Unsupported layered artwork extension")
            }
            return InkDocument(title=name.substringBeforeLast('.'), pages=pages)
        } finally {
            temporary.delete(); reservations.forEach(assets::releaseRasterTileReservation); masks.forEach(assets::releaseAssetReservation)
        }
    }

    private fun raw(page: InkPage, layer: InkLayer, width: Int, height: Int): PixelTileSurfaceSnapshot {
        val isolated = layer.copy(parentId=null, visible=true, opacity=1f, blendMode=LayerBlendMode.NORMAL,
            clippedToBelow=false, mask=null, colorReplacement=null, alphaLock=false)
        return assets.renderPageCompositeSurface(page.copy(layers=listOf(isolated)), width, height)
    }
    private fun exportStack(page: InkPage, uri: Uri, options: PageRasterExportOptions, width: Int, height: Int) {
        require(page.layers.none { it.role == LayerRole.ADJUSTMENT || it.colorReplacement != null }) {
            "Interchange export requires raster layers without adjustment layers or layer color replacement; flatten a copy first" }
        require(width.toLong()*height <= 16_777_216L) { "Layer interchange currently supports up to 16 megapixels; use native Inkloom packages/PNG/TIFF for larger pages" }
        var selected = page.layers.filter { it.role != LayerRole.BACKGROUND }
        require(selected.isNotEmpty())
        if(options.format == RasterFileFormat.OPEN_RASTER) require(selected.all {
            it.blendMode in oraModes && !it.clippedToBelow && (it.role != LayerRole.FOLDER || it.mask == null) && !it.alphaLock }) {
            "ORA supports standard blend modes and unmasked groups; use PSD/layered PNG for clipping or group masks" }
        if(options.format == RasterFileFormat.PSD) require(selected.all { it.blendMode in psdModes }) {
            "CSP glow modes have no exact PSD blend key; use layered PNG or flatten a copy first" }
        val paperFile = if(options.includeBackground) File.createTempFile("stack-paper-", ".png", context.cacheDir) else null
        val paperLayer = paperFile?.let { file ->
            assets.exportPagePng(page.copy(layers=page.layers.filter { it.role == LayerRole.BACKGROUND },elements=emptyList()), Uri.fromFile(file), options.copy(format=RasterFileFormat.PNG))
            InkLayer(name="Paper")
        }
        if(paperLayer != null) selected = listOf(paperLayer) + selected
        val composite = File.createTempFile("stack-preview-", ".png", context.cacheDir)
        try {
            assets.exportPagePng(page, Uri.fromFile(composite), options.copy(format=RasterFileFormat.PNG))
            context.contentResolver.openOutputStream(uri)?.use { output ->
                if(options.format == RasterFileFormat.PSD) {
                    val records = mutableListOf<PsdRasterCodec.Layer>()
                    fun layerMask(layer: InkLayer): ByteArray? = layer.mask?.surface?.let { assets.loadMaskTileSurface(it).snapshot() }?.let { source ->
                        requireNotNull(layer.mask).expressed(source).let { alpha -> ByteArray(width*height) { i -> alpha.alphaAt(i%width*alpha.logicalWidth/width, i/width*alpha.logicalHeight/height).toByte() } }
                    }
                    fun append(parent: String?) { selected.filter { it.parentId == parent }.asReversed().forEach { layer ->
                        if(layer.role == LayerRole.FOLDER) {
                            records += PsdRasterCodec.Layer(layer.name, blend=if(layer.passThrough) "pass" else psdModes.getValue(layer.blendMode),
                                opacity=(layer.opacity*255).toInt(), visible=layer.visible, section=if(layer.collapsed) 2 else 1,
                                mask=layerMask(layer),maskTop=0,maskLeft=0,maskWidth=width,maskHeight=height,maskEnabled=layer.mask?.enabled != false)
                            append(layer.id); records += PsdRasterCodec.Layer("</Layer group>", section=3)
                        } else {
                            val pixels = if(layer.id==paperLayer?.id) bitmapSurface(requireNotNull(paperFile)) else raw(page,layer,width,height)
                            val bounds = app.inkloom.engine.v5.tools.selectionContentBounds(pixels) ?: PixelRect(0,0,1,1)
                            val argb = IntArray(bounds.width*bounds.height) { i -> straight(pixels.pixelAt(bounds.left+i%bounds.width,bounds.top+i/bounds.width)) }
                            val mask = layerMask(layer)
                            records += PsdRasterCodec.Layer(layer.name,bounds.top,bounds.left,bounds.width,bounds.height,
                                psdModes.getValue(layer.blendMode),(layer.opacity*255).toInt(),layer.visible,layer.clippedToBelow,
                                pixels=argb,mask=mask,maskTop=0,maskLeft=0,maskWidth=width,maskHeight=height,maskEnabled=layer.mask?.enabled != false)
                        }
                    } }; append(null)
                    // PSD group masks are retained as mask channels on the section record.

                    withPngRows(composite) { row -> PsdRasterCodec.write(output,width,height,records,row) }
                } else ZipOutputStream(output.buffered()).use { zip ->
                    val type = "image/openraster".toByteArray(); val crc = CRC32().also { it.update(type) }
                    zip.putNextEntry(ZipEntry("mimetype").apply { method=ZipEntry.STORED; size=type.size.toLong(); compressedSize=size; this.crc=crc.value }); zip.write(type); zip.closeEntry()
                    val files = mutableMapOf<String,String>(); val maskFiles = mutableMapOf<String,String>()
                    selected.filter { it.role != LayerRole.FOLDER }.forEachIndexed { index,layer ->
                        val path = "data/layer-$index.png"; files[layer.id]=path
                        val source = if(layer.id==paperLayer?.id) bitmapSurface(requireNotNull(paperFile)) else if(options.format == RasterFileFormat.OPEN_RASTER) assets.renderPageCompositeSurface(page.copy(layers=listOf(layer.copy(parentId=null,visible=true,opacity=1f,blendMode=LayerBlendMode.NORMAL))),width,height) else raw(page,layer,width,height)
                        zip.putNextEntry(ZipEntry(path)); png(zip,source,page.dpi); zip.closeEntry()
                    }
                    if(options.format == RasterFileFormat.LAYERED_PNG) selected.forEachIndexed { index,layer -> layer.mask?.surface?.let { ref ->
                        val mask = assets.loadMaskTileSurface(ref).snapshot(); val path="data/mask-$index.png"; maskFiles[layer.id]=path
                        zip.putNextEntry(ZipEntry(path)); StreamingPng.write(zip,mask.logicalWidth,mask.logicalHeight,page.dpi) { y,row -> row.indices.forEach { x -> row[x]=(mask.alphaAt(x,y) shl 24) or 0xffffff } }; zip.closeEntry()
                    } }
                    val writer=StringWriter(); val xml=Xml.newSerializer(); xml.setOutput(writer); xml.startDocument("UTF-8",true)
                    xml.startTag(null,"image"); xml.attribute(null,"version","0.0.6"); xml.attribute(null,"w","$width"); xml.attribute(null,"h","$height"); xml.attribute(null,"xres","${page.dpi.toInt()}"); xml.attribute(null,"yres","${page.dpi.toInt()}")
                    xml.startTag(null,"stack")
                    fun append(parent:String?) { selected.filter { it.parentId==parent }.asReversed().forEach { layer ->
                        val tag=if(layer.role==LayerRole.FOLDER) "stack" else "layer"; xml.startTag(null,tag); xml.attribute(null,"name",layer.name)
                        xml.attribute(null,"opacity","${layer.opacity}"); xml.attribute(null,"visibility",if(layer.visible) "visible" else "hidden")
                        xml.attribute(null,"composite-op",oraModes[layer.blendMode] ?: "svg:src-over")
                        if(tag=="stack") { xml.attribute(null,"isolation",if(layer.passThrough) "auto" else "isolate"); append(layer.id) }
                        else xml.attribute(null,"src",files.getValue(layer.id))
                        xml.endTag(null,tag)
                    } }; append(null); xml.endTag(null,"stack"); xml.endTag(null,"image"); xml.endDocument()
                    zip.putNextEntry(ZipEntry("stack.xml")); zip.write(writer.toString().toByteArray()); zip.closeEntry()
                    if(options.format == RasterFileFormat.LAYERED_PNG) {
                        val metadata=Stack(width=width,height=height,dpi=page.dpi,layers=selected.map { it.copy(rasterTileSurface=null,mask=it.mask?.copy(surface=null)) },files=files,masks=maskFiles)
                        zip.putNextEntry(ZipEntry("inkloom-stack.json")); zip.write(json.encodeToString(metadata).toByteArray()); zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("mergedimage.png")); composite.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            } ?: error("Could not open artwork destination")
        } finally { composite.delete(); paperFile?.delete() }
    }

    private fun importPsd(image: PsdRasterCodec.Image, save:(PixelTileSurface)->RasterTileSurfaceRef, reservations:MutableList<String>): InkPage {
        val topOrder=mutableListOf<InkLayer>(); val parents=mutableListOf<String>()
        image.layers.forEach { source ->
            if(source.section==3) { require(parents.isNotEmpty()) { "Unbalanced PSD groups" }; parents.removeAt(parents.lastIndex); return@forEach }
            val blend=if(source.blend=="pass") LayerBlendMode.NORMAL else psdModes.entries.firstOrNull { it.value==source.blend }?.key
                ?: error("Unsupported PSD blend mode ${source.blend}")
            val role=if(source.section in 1..2) LayerRole.FOLDER else LayerRole.CUSTOM
            val ref=if(role==LayerRole.FOLDER) null else save(surface(image.width,image.height,source.pixels,source.width,source.height,source.left,source.top))
            val mask=source.mask?.let { alpha ->
                val mask=MaskTileSurface(image.width,image.height,initialDefaultAlpha=source.maskDefault)
                mask.beginWrite("import PSD mask").use { write ->
                    for(y in 0 until source.maskHeight) for(x in 0 until source.maskWidth) {
                        val dx=x+source.maskLeft; val dy=y+source.maskTop
                        if(dx in 0 until image.width && dy in 0 until image.height) write.setAlpha(dx,dy,alpha[y*source.maskWidth+x].toInt().and(255))
                    }; write.commit().close()
                }
                LayerMask(surface=assets.saveMaskTileSurface(mask.snapshot()).also { reservations+=it.assetPath },enabled=source.maskEnabled)
            }
            val layer=InkLayer(name=source.name,role=role,parentId=parents.lastOrNull(),rasterTileSurface=ref,mask=mask,
                blendMode=blend,opacity=source.opacity/255f,visible=source.visible,clippedToBelow=source.clipped,
                passThrough=source.blend=="pass",collapsed=source.section==2)
            topOrder+=layer; if(role==LayerRole.FOLDER) parents+=layer.id
        }
        require(parents.isEmpty()) { "Unbalanced PSD groups" }
        return InkPage(width=image.width.toFloat(),height=image.height.toFloat(),layers=topOrder.asReversed())
    }

    private fun importStack(file:File,save:(PixelTileSurface)->RasterTileSurfaceRef,reservations:MutableList<String>):InkPage = ZipFile(file).use { zip ->
        require(zip.size() <= 2048); val names=zip.entries().asSequence().map { it.name }.toList()
        require(names.size==names.distinct().size && names.all { !it.startsWith('/') && '\\' !in it && it.split('/').none { part -> part==".." } }) { "Unsafe archive paths" }
        fun text(path:String):String {
            val entry=requireNotNull(zip.getEntry(path)) { "Missing $path" }; require(entry.size in 0..2_097_152L)
            return zip.getInputStream(entry).use { input -> val bytes=input.readBoundedBytes(2_097_152, "Archive metadata exceeds 2 MB"); bytes.toString(Charsets.UTF_8) }
        }
        fun image(path:String):Bitmap {
            val entry=requireNotNull(zip.getEntry(path)); require(entry.size in 0..268_435_456L)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it,null,bounds) }
            require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong()*bounds.outHeight <= 16_777_216L) { "Layered image import currently supports 16 megapixels per source layer" }
            return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } ?: error("Invalid layer PNG")
        }
        if(zip.getEntry("inkloom-stack.json") != null) {
            val stack=json.decodeFromString<Stack>(text("inkloom-stack.json")); require(stack.version==1 && stack.width in 1..8192 && stack.height in 1..8192 && stack.layers.size<=512)
            require(stack.layers.all { it.rasterTileSurface==null && it.mask?.surface==null && it.role !in setOf(LayerRole.ADJUSTMENT,LayerRole.BACKGROUND) })
            val layers=stack.layers.map { layer ->
                val ref=stack.files[layer.id]?.let { path -> val bitmap=image(path); try { save(PixelTileBitmapAdapter.import(bitmap)) } finally { bitmap.recycle() } }
                val mask=stack.masks[layer.id]?.let { path -> val bitmap=image(path); try {
                    val surface=MaskTileSurface(bitmap.width,bitmap.height)
                    surface.beginWrite("import mask").use { write ->
                        val row=IntArray(bitmap.width); for(y in 0 until bitmap.height) { bitmap.getPixels(row,0,row.size,0,y,row.size,1); write.replaceAlpha(PixelRect(0,y,row.size,y+1),ByteArray(row.size) { (row[it] ushr 24).toByte() }) }; write.commit().close()
                    }
                    requireNotNull(layer.mask).copy(surface=assets.saveMaskTileSurface(surface.snapshot()).also { reservations+=it.assetPath })
                } finally { bitmap.recycle() } } ?: layer.mask
                layer.copy(rasterTileSurface=ref,mask=mask)
            }
            require(layers.map(InkLayer::id).distinct().size==layers.size && layers.all { it.parentId==null || layers.any { parent -> parent.id==it.parentId && parent.role==LayerRole.FOLDER } })
            layers.forEach { layer -> val seen=hashSetOf(layer.id); var parent=layer.parentId
                while(parent!=null) { require(seen.add(parent)) { "Cyclic layer hierarchy" }; parent=layers.first { it.id==parent }.parentId }
            }
            return InkPage(width=stack.width.toFloat(),height=stack.height.toFloat(),dpi=stack.dpi,layers=layers)
        }
        val xml=text("stack.xml"); require(!xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "XML entities are not permitted" }
        val parser=Xml.newPullParser(); parser.setInput(StringReader(xml)); val layers=mutableListOf<InkLayer>(); val groups=mutableListOf<String?>()
        var width=0; var height=0; var dpi=72f
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG) {
                fun attr(name:String)=parser.getAttributeValue(null,name)
                when(parser.name) {
                    "image" -> { width=attr("w")!!.toInt(); height=attr("h")!!.toInt(); require(width in 1..8192 && height in 1..8192); dpi=attr("xres")?.toFloat() ?: 72f }
                    "stack" -> if(groups.isEmpty()) groups+=null else {
                        val mode=attr("composite-op") ?: "svg:src-over"
                        val blend=oraModes.entries.firstOrNull { it.value==mode }?.key ?: error("Unsupported ORA blend $mode")
                        val group=InkLayer(name=attr("name") ?: "Group",role=LayerRole.FOLDER,parentId=groups.last(),
                            visible=attr("visibility")!="hidden",opacity=(attr("opacity") ?: "1").toFloat().also { require(it in 0f..1f) },
                            blendMode=blend,passThrough=attr("isolation")=="auto")
                        require(layers.size<512); layers+=group; groups+=group.id
                    }
                    "layer" -> {
                        require(width>0 && groups.isNotEmpty() && layers.size<512)
                        val blend=oraModes.entries.firstOrNull { it.value==(attr("composite-op") ?: "svg:src-over") }?.key ?: error("Unsupported ORA blend")
                        val bitmap=image(requireNotNull(attr("src"))); val ref=try {
                            val pixels=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                            save(surface(width,height,pixels,bitmap.width,bitmap.height,attr("x")?.toInt() ?: 0,attr("y")?.toInt() ?: 0))
                        } finally { bitmap.recycle() }
                        layers+=InkLayer(name=attr("name") ?: "Layer",parentId=groups.last(),rasterTileSurface=ref,blendMode=blend,
                            visible=attr("visibility")!="hidden",opacity=(attr("opacity") ?: "1").toFloat().also { require(it in 0f..1f) })
                    }
                    else -> error("Unsupported ORA structure ${parser.name}")
                }
            } else if(parser.eventType==XmlPullParser.END_TAG && parser.name=="stack") { require(groups.isNotEmpty()); groups.removeAt(groups.lastIndex) }
            parser.next()
        }
        require(width>0 && layers.isNotEmpty() && groups.isEmpty())
        InkPage(width=width.toFloat(),height=height.toFloat(),dpi=dpi,layers=layers.asReversed())
    }

    private fun surface(width:Int,height:Int,pixels:IntArray,sourceWidth:Int=width,sourceHeight:Int=height,left:Int=0,top:Int=0):PixelTileSurface {
        val surface=PixelTileSurface(width,height)
        surface.beginWrite("import artwork").use { write ->
            for(y in 0 until sourceHeight) for(x in 0 until sourceWidth) {
                val dx=x+left; val dy=y+top; val p=pixels[y*sourceWidth+x]
                if(dx in 0 until width && dy in 0 until height && p ushr 24 != 0) {
                    val a=p ushr 24; val premult=(a shl 24) or ((p ushr 16 and 255)*a/255 shl 16) or ((p ushr 8 and 255)*a/255 shl 8) or ((p and 255)*a/255)
                    write.setPixel(dx,dy,premult)
                }
            }; write.commit().close()
        }; return surface
    }
    private fun bitmapSurface(file:File):PixelTileSurfaceSnapshot {
        val bitmap=BitmapFactory.decodeFile(file.path) ?: error("Could not decode paper")
        return try { PixelTileBitmapAdapter.import(bitmap).snapshot() } finally { bitmap.recycle() }
    }
    private fun straight(p:Int):Int {
        val a=p ushr 24; if(a==0) return 0
        return (a shl 24) or ((p ushr 16 and 255)*255/a).coerceAtMost(255).shl(16) or ((p ushr 8 and 255)*255/a).coerceAtMost(255).shl(8) or ((p and 255)*255/a).coerceAtMost(255)
    }
    private fun png(output:OutputStream,source:PixelTileSurfaceSnapshot,dpi:Float) = StreamingPng.write(output,source.logicalWidth,source.logicalHeight,dpi) { y,row ->
        row.indices.forEach { x -> row[x]=straight(source.pixelAt(x,y)) }
    }
    private fun withPngRows(file:File,block:((Int,IntArray)->Unit)->Unit) {
        val decoder=BitmapRegionDecoder.newInstance(file.path,false); var strip:Bitmap?=null; var start=-1
        try { block { y,row ->
            if(strip==null || y<start || y>=start+requireNotNull(strip).height) {
                strip?.recycle(); start=y; strip=decoder.decodeRegion(Rect(0,y,decoder.width,minOf(y+128,decoder.height)),null)
            }
            requireNotNull(strip).getPixels(row,0,row.size,0,y-start,row.size,1)
        } } finally { strip?.recycle(); decoder.recycle() }
    }
}
