package app.inkloom.document

enum class PageSearchSource { PAGE, OBJECT, HANDWRITING, IMAGE_OCR, PDF }

data class PageSearchHit(val pageId: String, val snippet: String, val source: PageSearchSource,
    val bounds: PdfTextBounds? = null, val recognizedBlockId: String? = null,
    val occurrenceIndex: Int = 0, val matchStart: Int = 0, val matchLength: Int = 0)

object PageSearch {
    fun find(page: InkPage, rawQuery: String): PageSearchHit? = findAll(page, rawQuery).firstOrNull()
    fun findAll(page: InkPage, rawQuery: String): List<PageSearchHit> {
        val query=rawQuery.trim(); if(query.isEmpty()) return emptyList()
        val result=mutableListOf<PageSearchHit>()
        fun add(text:String, source:PageSearchSource, bounds:PdfTextBounds?=null, blockId:String?=null) {
            var position=0
            while(position <= text.length-query.length) {
                val index=text.indexOf(query,position,ignoreCase=true); if(index<0) break
                val start=(index-52).coerceAtLeast(0); val end=(index+query.length+52).coerceAtMost(text.length)
                val snippet=(if(start>0) "…" else "")+text.substring(start,end).replace(Regex("\\s+")," ").trim()+(if(end<text.length) "…" else "")
                result+=PageSearchHit(page.id,snippet,source,bounds,blockId,result.size,index,query.length)
                position=index+query.length
            }
        }
        add(page.title,PageSearchSource.PAGE); page.customNumber?.let { add(it,PageSearchSource.PAGE) }
        page.tags.forEach { add("#$it",PageSearchSource.PAGE) }
        page.elements.forEach { element ->
            val text=when(element) { is PageElement.Text -> element.text; is PageElement.Sticky -> element.text; is PageElement.Link -> "${element.title}\n${element.url}"; else -> null }
            text?.let { val b=element.displayBounds(page.width,page.height)
                add(it,PageSearchSource.OBJECT,PdfTextBounds(b.minOf { it.x }/page.width,b.minOf { it.y }/page.height,b.maxOf { it.x }/page.width,b.maxOf { it.y }/page.height)) }
        }
        page.recognizedTextBlocks.filter { block -> when(block.source) {
            RecognizedTextSource.HANDWRITING -> page.handwritingIndexFingerprint==HandwritingIndex.fingerprint(page)
            RecognizedTextSource.IMAGE_OCR -> page.imageOcrFingerprint==ImageOcrIndex.fingerprint(page)
        } }.forEach { add(it.text,if(it.source==RecognizedTextSource.HANDWRITING) PageSearchSource.HANDWRITING else PageSearchSource.IMAGE_OCR,it.bounds,it.id) }
        page.pdfTextBlocks.forEach { add(it.text,PageSearchSource.PDF,it.bounds.firstOrNull()) }
        return result
    }
    fun matches(page:InkPage,rawQuery:String):Boolean=rawQuery.isBlank() || find(page,rawQuery)!=null
    fun nextIndex(current:Int,count:Int,delta:Int):Int?=if(count<=0) null else (current+delta).mod(count)
}
