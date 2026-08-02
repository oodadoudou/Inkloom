package app.inkloom.document

object LassoGeometry {
    fun contains(polygon: List<StrokePoint>, point: StrokePoint): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val crossingX = (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x
                if (point.x < crossingX) inside = !inside
            }
            previous = current
        }
        return inside
    }

    fun selects(polygon: List<StrokePoint>, stroke: InkStroke): Boolean = stroke.points.any { contains(polygon, it) }
}
