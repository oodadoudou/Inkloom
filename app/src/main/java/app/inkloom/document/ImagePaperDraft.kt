package app.inkloom.document

import java.util.concurrent.atomic.AtomicBoolean

/** Protects the copied image until the user saves or abandons the template preview. */
class ImagePaperDraft(val template: PageTemplate, private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}
