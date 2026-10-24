package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID

/** One image in the Sub view: a private copy kept by the app, never part of a notebook. */
@Serializable
data class ReferenceImage(val id: String, val name: String, val file: String, val addedAt: Long)

/**
 * CSP's Sub view images: copies of local pictures to look at and pick colours from while drawing.
 * They live in app storage, outside every notebook, so they are never exported or edited.
 */
class ReferenceImageStore(root: File) {
    private val directory = File(root, "reference-images")
    private val index = File(directory, "index.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun list(): List<ReferenceImage> = runCatching {
        if (!index.isFile) emptyList() else json.decodeFromString(ListSerializer(ReferenceImage.serializer()), index.readText())
            .filter { File(directory, it.file).isFile }
    }.getOrDefault(emptyList())

    /** Copies [input] in as a new image at the end of the list; fails without changing the list if it is too large. */
    @Synchronized fun add(name: String, extension: String, input: InputStream): List<ReferenceImage> {
        val images = list()
        require(images.size < MAX_IMAGES) { "The Sub view holds up to $MAX_IMAGES images; remove some first" }
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val fileName = "$id.${extension.lowercase().filter(Char::isLetterOrDigit).take(5).ifBlank { "img" }}"
        val target = File(directory, fileName)
        val partial = File(directory, "$fileName.part")
        try {
            partial.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Images for the Sub view must be smaller than ${MAX_BYTES / (1024 * 1024)} MB" }
                    out.write(buffer, 0, count)
                }
            }
            check(partial.renameTo(target)) { "Could not save the image" }
        } finally { partial.delete() }
        val next = images + ReferenceImage(id, name.trim().take(80).ifBlank { "Image" }, fileName, System.currentTimeMillis())
        write(next)
        return next
    }

    @Synchronized fun remove(id: String): List<ReferenceImage> {
        val images = list()
        val removed = images.firstOrNull { it.id == id } ?: return images
        val next = images - removed
        write(next)
        File(directory, removed.file).delete()
        return next
    }

    fun file(image: ReferenceImage): File = File(directory, image.file)

    private fun write(images: List<ReferenceImage>) {
        directory.mkdirs()
        val temporary = File(directory, "index.json.tmp")
        temporary.writeText(json.encodeToString(ListSerializer(ReferenceImage.serializer()), images))
        check(temporary.renameTo(index)) { "Could not save the Sub view list" }
    }

    companion object {
        const val MAX_IMAGES = 50
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
