package app.inkloom.document

import java.security.MessageDigest

object HandwritingIndex {
    /** V5 handwriting truth is the raster-layer generation, never serialized vector strokes. */
    fun fingerprint(page: InkPage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        page.layers.filter(InkLayer::isPaintSurfaceLayer).forEach { layer ->
            digest.update(layer.id.encodeToByteArray())
            layer.rasterTileSurface?.let { reference ->
                digest.update(reference.surfaceId.encodeToByteArray())
                digest.update(reference.revision.toString().encodeToByteArray())
                digest.update(reference.manifestAssetPath.encodeToByteArray())
            }
            layer.rasterAssetPath?.let { digest.update(it.encodeToByteArray()) }
            digest.update(0.toByte())
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

}
