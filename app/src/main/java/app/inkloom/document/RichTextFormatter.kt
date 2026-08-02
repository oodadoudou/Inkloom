package app.inkloom.document

object RichTextFormatter {
    fun lines(text: String, listStyle: TextListStyle): List<String> = text.lines().mapIndexed { index, line ->
        when (listStyle) {
            TextListStyle.NONE -> line
            TextListStyle.BULLET -> "• $line"
            TextListStyle.NUMBERED -> "${index + 1}. $line"
            TextListStyle.CHECKBOX -> "☐ $line"
        }
    }

    fun displayText(text: String, listStyle: TextListStyle): String = lines(text, listStyle).joinToString("\n")
}
