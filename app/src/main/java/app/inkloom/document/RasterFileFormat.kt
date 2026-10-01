package app.inkloom.document

/** Actual file encodings; layered PNG is an explicit ZIP containing stack.xml and PNGs. */
enum class RasterFileFormat(val extension: String, val mime: String, val label: String) {
    PNG("png", "image/png", "PNG"), JPEG("jpg", "image/jpeg", "JPEG"),
    TIFF("tif", "image/tiff", "TIFF"), OPEN_RASTER("ora", "image/openraster", "OpenRaster"),
    PSD("psd", "image/vnd.adobe.photoshop", "PSD"), LAYERED_PNG("layers.zip", "application/zip", "Layered PNG")
}
