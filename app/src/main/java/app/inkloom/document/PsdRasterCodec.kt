package app.inkloom.document

import java.io.*
import kotlin.math.abs

/** PSD v1, 8-bit RGB raster layers/groups, standard blend keys, clipping and user masks.
 * Raw and PackBits channels are accepted. Smart objects, text, effects and adjustments are rejected.
 */
object PsdRasterCodec {
    data class Layer(val name: String, val top: Int = 0, val left: Int = 0, val width: Int = 0, val height: Int = 0,
        val blend: String = "norm", val opacity: Int = 255, val visible: Boolean = true, val clipped: Boolean = false,
        val section: Int = 0, val pixels: IntArray = IntArray(0), val mask: ByteArray? = null,
        val maskTop: Int = top, val maskLeft: Int = left, val maskWidth: Int = width, val maskHeight: Int = height,
        val maskDefault: Int = 255, val maskEnabled: Boolean = true)
    data class Image(val width: Int, val height: Int, val layers: List<Layer>)
    private data class Channel(val id: Int, val length: Long)
    private data class Record(val layer: Layer, val channels: List<Channel>)
    private val allowed = setOf("luni", "lsct", "lsdk", "lyid", "clbl", "infx", "knko", "lspf", "lclr", "fxrp", "shmd", "lnsr", "lyvr", "tsly")
    private fun DataOutputStream.code(value: String) { require(value.length == 4); write(value.toByteArray(Charsets.US_ASCII)) }
    private fun RandomAccessFile.code() = ByteArray(4).also(::readFully).toString(Charsets.US_ASCII)
    private fun bytes(block: (DataOutputStream) -> Unit) = ByteArrayOutputStream().also { out -> DataOutputStream(out).use(block) }.toByteArray()

    fun write(output: OutputStream, width: Int, height: Int, layers: List<Layer>, row: (Int, IntArray) -> Unit) {
        require(width in 1..8192 && height in 1..8192 && layers.size in 1..512)
        val records = layers.map { layer ->
            require(layer.width >= 0 && layer.height >= 0 && layer.width.toLong()*layer.height <= 67_108_864L)
            require(layer.pixels.size == layer.width*layer.height)
            val channels = (if (layer.section == 0) listOf(-1, 0, 1, 2) else emptyList()) + if(layer.mask != null) listOf(-2) else emptyList()
            bytes { record ->
                record.writeInt(layer.top); record.writeInt(layer.left); record.writeInt(layer.top+layer.height); record.writeInt(layer.left+layer.width)
                record.writeShort(channels.size)
                channels.forEach { id -> record.writeShort(id); record.writeInt(2 + if(id == -2) layer.maskWidth*layer.maskHeight else layer.width*layer.height) }
                record.code("8BIM"); record.code(layer.blend); record.writeByte(layer.opacity); record.writeByte(if(layer.clipped) 1 else 0)
                record.writeByte(if(layer.visible) 0 else 2); record.writeByte(0)
                val extra = bytes { e ->
                    if(layer.mask == null) e.writeInt(0) else { require(layer.mask.size == layer.maskWidth*layer.maskHeight); e.writeInt(20)
                        e.writeInt(layer.maskTop); e.writeInt(layer.maskLeft); e.writeInt(layer.maskTop+layer.maskHeight); e.writeInt(layer.maskLeft+layer.maskWidth)
                        e.writeByte(layer.maskDefault); e.writeByte(if(layer.maskEnabled) 0 else 2); e.writeShort(0)
                    }
                    e.writeInt(0) // blending ranges
                    val name = layer.name.toByteArray(Charsets.ISO_8859_1).take(255).toByteArray()
                    e.writeByte(name.size); e.write(name); repeat((4-(name.size+1)%4)%4) { e.writeByte(0) }
                    fun block(key: String, payload: ByteArray) { e.code("8BIM"); e.code(key); e.writeInt(payload.size); e.write(payload); if(payload.size%2 != 0) e.writeByte(0) }
                    block("luni", bytes { u -> u.writeInt(layer.name.length); layer.name.forEach { u.writeChar(it.code) } })
                    if(layer.section != 0) block("lsct", bytes { it.writeInt(layer.section) })
                }
                record.writeInt(extra.size); record.write(extra)
            }
        }
        val channelLength = layers.sumOf { layer -> (if(layer.section != 0) 0L else 4L*(2L+layer.width.toLong()*layer.height)) + (layer.mask?.let { 2L+it.size } ?: 0L) }
        val infoLength = 2L + records.sumOf { it.size.toLong() } + channelLength
        require(infoLength + 8 <= Int.MAX_VALUE) { "PSD layer data exceeds supported size" }
        val out = DataOutputStream(output)
        out.code("8BPS"); out.writeShort(1); out.write(ByteArray(6)); out.writeShort(4); out.writeInt(height); out.writeInt(width); out.writeShort(8); out.writeShort(3)
        out.writeInt(0); out.writeInt(0)
        out.writeInt((4+infoLength+infoLength%2+4).toInt()); out.writeInt((infoLength+infoLength%2).toInt())
        out.writeShort(-layers.size) // negative count declares merged alpha channel
        records.forEach(out::write)
        val line = ByteArray(maxOf(width, layers.maxOf { it.width }))
        layers.forEach { layer -> if(layer.section == 0) {
            for(channel in listOf(3, 0, 1, 2)) { out.writeShort(0)
                for(y in 0 until layer.height) { for(x in 0 until layer.width) {
                    val p = layer.pixels[y*layer.width+x]; line[x] = (p ushr when(channel) { 0 -> 16; 1 -> 8; 2 -> 0; else -> 24 }).toByte()
                }; out.write(line, 0, layer.width) }
            }
        }; layer.mask?.let { out.writeShort(0); out.write(it) } }
        if(infoLength%2 != 0L) out.writeByte(0); out.writeInt(0)
        out.writeShort(0); val pixels = IntArray(width)
        for(channel in listOf(0, 1, 2, 3)) for(y in 0 until height) {
            row(y, pixels); for(x in 0 until width) line[x] = (pixels[x] ushr when(channel) { 0 -> 16; 1 -> 8; 2 -> 0; else -> 24 }).toByte()
            out.write(line, 0, width)
        }
        out.flush()
    }

    fun read(file: File): Image = RandomAccessFile(file, "r").use { input ->
        fun size(): Long = input.readInt().toLong().and(0xffffffffL).also { require(it <= input.length()-input.filePointer) { "Truncated PSD section" } }
        fun skipSection() { val length = size(); input.seek(input.filePointer+length) }
        require(input.code() == "8BPS" && input.readUnsignedShort() == 1) { "PSD v1 required; PSB is unsupported" }
        input.skipBytes(6); val compositeChannels = input.readUnsignedShort(); val h = input.readInt(); val w = input.readInt()
        require(w in 1..8192 && h in 1..8192 && compositeChannels in 3..4 && input.readUnsignedShort() == 8 && input.readUnsignedShort() == 3) { "PSD requires 8-bit RGB, up to 8192 pixels" }
        skipSection(); skipSection(); val layerSection = size(); val sectionEnd = input.filePointer + layerSection
        val records = mutableListOf<Record>()
        if(layerSection > 0) {
            val info = size(); val infoEnd = input.filePointer+info; require(infoEnd <= sectionEnd)
            if(info > 0) {
                val count = abs(input.readShort().toInt()); require(count in 1..512)
                repeat(count) {
                    val top = input.readInt(); val left = input.readInt(); val bottom = input.readInt(); val right = input.readInt()
                    val width = right-left; val height = bottom-top
                    require(width in 0..8192 && height in 0..8192 && width.toLong()*height <= 67_108_864L)
                    val channelCount = input.readUnsignedShort(); require(channelCount in 0..5)
                    val channels = List(channelCount) { Channel(input.readShort().toInt(), size()) }
                    require(channels.all { it.id in listOf(-2, -1, 0, 1, 2) }) { "PSD spot / extra mask channels are unsupported" }
                    require(input.code() == "8BIM"); var blend = input.code(); val opacity = input.readUnsignedByte(); val clip = input.readUnsignedByte(); val flags = input.readUnsignedByte(); input.readByte()
                    val extraLength = size(); val extraEnd = input.filePointer + extraLength; require(extraEnd <= infoEnd)
                    val maskLength = size(); val maskEnd = input.filePointer+maskLength
                    var mt = top; var ml = left; var mw = width; var mh = height; var md = 255; var me = true
                    if(maskLength > 0) {
                        require(maskLength >= 20); mt = input.readInt(); ml = input.readInt(); val mb = input.readInt(); val mr = input.readInt(); mh = mb-mt; mw = mr-ml
                        require(mw in 0..8192 && mh in 0..8192); md = input.readUnsignedByte(); val mf = input.readUnsignedByte()
                        require(mf and 24 == 0) { "PSD mask parameters / real masks are unsupported" }
                        if(mf and 1 != 0) { mt += top; ml += left }; me = mf and 2 == 0
                        require(mf and 4 == 0) { "Inverted PSD masks must be applied before import" }
                    }
                    input.seek(maskEnd); skipSection(); val nameLength = input.readUnsignedByte(); val nameBytes = ByteArray(nameLength).also(input::readFully)
                    var name = nameBytes.toString(Charsets.ISO_8859_1); input.skipBytes((4-(nameLength+1)%4)%4); var section = 0
                    while(input.filePointer+12 <= extraEnd) {
                        require(input.code() == "8BIM"); val key = input.code(); val length = size(); val end = input.filePointer+length
                        require(end <= extraEnd && key in allowed) { "PSD layer feature $key is unsupported; export raster layers without effects" }
                        when(key) {
                            "luni" -> { val units = input.readInt(); require(units in 0..16384 && units*2L <= length-4); name = CharArray(units) { input.readChar() }.concatToString() }
                            "lsct", "lsdk" -> { section = input.readInt(); if(length >= 12) { require(input.code() == "8BIM"); blend = input.code() } }
                        }
                        input.seek(end+length%2)
                    }
                    input.seek(extraEnd)
                    records += Record(Layer(name, top, left, width, height, blend, opacity, flags and 2 == 0, clip != 0,
                        section, maskTop = mt, maskLeft = ml, maskWidth = mw, maskHeight = mh, maskDefault = md, maskEnabled = me), channels)
                }
                records.indices.forEach { index ->
                    val record = records[index]; val layer = record.layer
                    val pixels = IntArray(layer.width*layer.height) { -0x1000000 }; var mask: ByteArray? = null
                    record.channels.forEach { channel ->
                        val end = input.filePointer+channel.length; require(end <= infoEnd)
                        val width = if(channel.id == -2) layer.maskWidth else layer.width
                        val height = if(channel.id == -2) layer.maskHeight else layer.height
                        val compression = input.readUnsignedShort(); require(compression in 0..1) { "PSD ZIP compression is unsupported; use raw or RLE" }
                        val lengths = if(compression == 1) IntArray(height) { input.readUnsignedShort() } else null
                        val bytes = ByteArray(width); if(channel.id == -2) mask = ByteArray(width*height)
                        for(y in 0 until height) {
                            if(lengths == null) input.readFully(bytes) else {
                                val compressed = ByteArray(lengths[y]).also(input::readFully); decodePackBits(compressed, bytes)
                            }
                            if(channel.id == -2) bytes.copyInto(requireNotNull(mask), y*width) else {
                                val shift = when(channel.id) { -1 -> 24; 0 -> 16; 1 -> 8; else -> 0 }
                                for(x in 0 until width) pixels[y*width+x] = (pixels[y*width+x] and (255 shl shift).inv()) or (bytes[x].toInt().and(255) shl shift)
                            }
                        }
                        require(input.filePointer == end) { "PSD channel byte count mismatch" }
                    }
                    records[index] = record.copy(layer = layer.copy(pixels = pixels, mask = mask))
                }
            }
        }
        if(records.isEmpty()) {
            input.seek(sectionEnd); val compression = input.readUnsignedShort(); require(compression in 0..1)
            val counts = if(compression == 1) IntArray(h*compositeChannels) { input.readUnsignedShort() } else null
            val pixels = IntArray(w*h) { -0x1000000 }; val row = ByteArray(w)
            repeat(compositeChannels) { c -> repeat(h) { y ->
                if(counts == null) input.readFully(row) else decodePackBits(ByteArray(counts[c*h+y]).also(input::readFully), row)
                val shift = listOf(16,8,0,24)[c]; repeat(w) { x -> pixels[y*w+x] = (pixels[y*w+x] and (255 shl shift).inv()) or (row[x].toInt().and(255) shl shift) }
            } }; return Image(w, h, listOf(Layer("Composite", width=w, height=h, pixels=pixels)))
        }
        Image(w, h, records.map(Record::layer))
    }

    private fun decodePackBits(source: ByteArray, output: ByteArray) {
        var i = 0; var p = 0
        while(i < source.size) { val n = source[i++].toInt(); when {
            n >= 0 -> { val count = n+1; require(i+count <= source.size && p+count <= output.size); source.copyInto(output,p,i,i+count); i+=count; p+=count }
            n >= -127 -> { val count = 1-n; require(i < source.size && p+count <= output.size); output.fill(source[i++],p,p+count); p+=count }
        } }; require(p == output.size) { "Incomplete PSD RLE row" }
    }
}
