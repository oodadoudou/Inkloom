package app.inkloom.document

import java.security.MessageDigest
import java.io.File

object ImageOcrIndex {
    fun fingerprint(page: InkPage): String {
        val sources=buildList<Pair<String,String>> {
            when(val background=page.background) {
                is PageBackground.Image -> add(background.assetPath to "image:$background")
                is PageBackground.Pdf -> add(background.assetPath to "pdf:${background.pageIndex}")
                else -> Unit
            }
            page.elements.filterIsInstance<PageElement.Image>().forEach { add(it.assetPath to it.toString()) }
        }
        if(sources.isEmpty()) return ""
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update("${page.width}:${page.height}:${page.crop}:${page.backgroundSourceCrop}:${page.backgroundFrame}:${page.backgroundRotationQuarterTurns}".toByteArray())
        sources.sortedBy { it.second }.forEach { (path,geometry) ->
            digest.update(geometry.toByteArray()); val file=File(path)
            if(file.isFile) digest.update("${file.length()}:${file.lastModified()}".toByteArray())
        }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }
}
