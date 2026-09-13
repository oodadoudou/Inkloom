package app.inkloom.document

import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import java.io.File
import java.lang.ref.WeakReference

/** The editor's history and the library's collector must see the same reservations. */
internal object DocumentAssetLifecycles {
    private val owners = mutableMapOf<String, WeakReference<DocumentAssetLifecycle>>()

    @Synchronized
    fun forDirectory(directory: File, tileStore: PixelTileArchiveStore): DocumentAssetLifecycle {
        val key = directory.canonicalPath
        owners[key]?.get()?.let { return it }
        owners.entries.removeAll { it.value.get() == null }
        return DocumentAssetLifecycle(directory, tileStore).also { owners[key] = WeakReference(it) }
    }
}
