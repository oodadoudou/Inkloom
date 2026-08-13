package app.inkloom.document

data class PositionedStroke(val index: Int, val stroke: InkStroke)

data class StrokeEdit(
    val before: List<PositionedStroke>,
    val after: List<PositionedStroke>,
)

object StrokeEdits {
    fun difference(before: List<InkStroke>, after: List<InkStroke>): StrokeEdit? {
        val beforeById = before.associateBy(InkStroke::id)
        val afterById = after.associateBy(InkStroke::id)
        val changedIds = (beforeById.keys + afterById.keys).filterTo(linkedSetOf()) { id ->
            beforeById[id] != afterById[id]
        }
        if (changedIds.isEmpty()) return null
        return StrokeEdit(
            before = before.mapIndexedNotNull { index, stroke -> stroke.takeIf { it.id in changedIds }?.let { PositionedStroke(index, it) } },
            after = after.mapIndexedNotNull { index, stroke -> stroke.takeIf { it.id in changedIds }?.let { PositionedStroke(index, it) } },
        )
    }

    fun apply(
        current: List<InkStroke>,
        remove: List<PositionedStroke>,
        insert: List<PositionedStroke>,
    ): List<InkStroke> {
        val affectedIds = (remove + insert).mapTo(mutableSetOf()) { it.stroke.id }
        return current.filterNot { it.id in affectedIds }.toMutableList().apply {
            insert.sortedBy(PositionedStroke::index).forEach { positioned ->
                add(positioned.index.coerceIn(0, size), positioned.stroke)
            }
        }
    }
}
