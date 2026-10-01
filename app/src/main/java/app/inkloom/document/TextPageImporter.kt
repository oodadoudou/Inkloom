package app.inkloom.document

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** UTF-8 plain text/Markdown becomes editable, sequential notebook pages. */
internal object TextPageImporter {
    fun read(input: InputStream, name: String): List<InkPage> {
        val bytes=input.readBoundedBytes(4_194_304, "Text append supports up to 4 MB")
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\ufeff").replace("\r\n","\n").replace('\r','\n')
        require(text.isNotBlank()) { "Text file is empty" }
        val paint=TextPaint().apply { textSize=36f; color=Color.BLACK; isAntiAlias=true }
        val layout=StaticLayout.Builder.obtain(text,0,text.length,paint,1408).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        val pages=mutableListOf<InkPage>(); var line=0
        while(line<layout.lineCount) {
            require(pages.size<512) { "Text append exceeds 512 pages" }
            val first=line; val top=layout.getLineTop(first)
            while(line+1<layout.lineCount && layout.getLineBottom(line+1)-top<=2000) line++
            val body=text.substring(layout.getLineStart(first),layout.getLineEnd(line))
            pages+=InkPage(title="${name.substringBeforeLast('.')} · ${pages.size+1}",width=1600f,height=2263f,
                elements=listOf(PageElement.Text(x=.06f,y=.06f,width=1408f,height=2000f,text=body,textSize=36f)))
            line++
        }; return pages
    }
}
