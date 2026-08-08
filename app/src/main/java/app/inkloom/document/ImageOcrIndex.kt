package app.inkloom.document

import java.security.MessageDigest
import java.io.File

object ImageOcrIndex {
    fun fingerprint(page: InkPage): String {
        val sources = buildList {
            (page.background as? PageBackground.Image)?.assetPath?.let { add("background:$it") }
            page.elements.filterIsInstance<PageElement.Image>().forEach { image ->
                add("${image.id}:${image.assetPath}:${image.crop}:${image.rotationDegrees}")
            }
        }
        if (sources.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        sources.sorted().forEach { source ->
            digest.update(source.encodeToByteArray())
            source.substringAfter(':').substringBefore(':').let(::File).takeIf(File::isFile)?.let { file ->
                digest.update(file.length().toString().encodeToByteArray())
                digest.update(file.lastModified().toString().encodeToByteArray())
            }
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }
}
