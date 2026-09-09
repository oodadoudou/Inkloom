package app.inkloom.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Queries only the tree granted by Android's folder picker, one directory at a time. */
class BrushDocumentTree(private val resolver: ContentResolver, private val tree: Uri) {
    fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree,id)
    fun scan(cancelled: () -> Boolean = { false }): BrushFolderImport.Plan {
        val id=DocumentsContract.getTreeDocumentId(tree)
        val root=query(uri(id)).singleOrNull() ?: error("Selected folder is no longer readable")
        return BrushFolderImport.scan(root, { parent -> query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)) }, cancelled)
    }
    private fun query(uri: Uri): List<BrushFolderImport.Node> {
        val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE)
        return requireNotNull(resolver.query(uri,columns,null,null,null)) { "Folder provider returned no listing" }.use { cursor ->
            buildList { while(cursor.moveToNext()) add(BrushFolderImport.Node(cursor.getString(0),cursor.getString(1).orEmpty(),cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR)) }
        }
    }
}
