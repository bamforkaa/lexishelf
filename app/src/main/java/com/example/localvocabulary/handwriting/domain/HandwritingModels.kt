package com.example.localvocabulary.handwriting.domain

data class HandwritingPoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Ink coordinates must be finite" }
        require(timestampMillis >= 0) { "Ink timestamps must be non-negative" }
    }
}

data class HandwritingStroke(
    val points: List<HandwritingPoint>,
) {
    init {
        require(points.isNotEmpty()) { "A handwriting stroke must contain a point" }
        require(points.zipWithNext().all { (left, right) ->
            left.timestampMillis <= right.timestampMillis
        }) { "Stroke timestamps must be ordered" }
    }
}

data class HandwritingInk(
    val strokes: List<HandwritingStroke> = emptyList(),
    val activePoints: List<HandwritingPoint> = emptyList(),
) {
    val isEmpty: Boolean
        get() = strokes.isEmpty() && activePoints.isEmpty()

    fun begin(point: HandwritingPoint): HandwritingInk {
        val completed = activePoints.takeIf(List<HandwritingPoint>::isNotEmpty)?.let {
            strokes + HandwritingStroke(it)
        } ?: strokes
        return copy(strokes = completed, activePoints = listOf(point))
    }

    fun append(point: HandwritingPoint): HandwritingInk {
        if (activePoints.isEmpty()) return begin(point)
        return copy(activePoints = activePoints + point.orderedAfter(activePoints.last()))
    }

    fun end(point: HandwritingPoint? = null): HandwritingInk {
        if (activePoints.isEmpty()) return this
        val completedPoints = point?.let { activePoints + it.orderedAfter(activePoints.last()) }
            ?: activePoints
        return copy(
            strokes = strokes + HandwritingStroke(completedPoints),
            activePoints = emptyList(),
        )
    }

    fun undoLastStroke(): HandwritingInk = when {
        activePoints.isNotEmpty() -> copy(activePoints = emptyList())
        strokes.isNotEmpty() -> copy(strokes = strokes.dropLast(1))
        else -> this
    }

    fun clear(): HandwritingInk = HandwritingInk()

    fun scaled(scaleX: Float, scaleY: Float): HandwritingInk {
        require(scaleX.isFinite() && scaleX > 0f)
        require(scaleY.isFinite() && scaleY > 0f)
        fun HandwritingPoint.scale() = copy(x = x * scaleX, y = y * scaleY)
        return copy(
            strokes = strokes.map { stroke ->
                HandwritingStroke(stroke.points.map { it.scale() })
            },
            activePoints = activePoints.map { it.scale() },
        )
    }

    private fun HandwritingPoint.orderedAfter(previous: HandwritingPoint): HandwritingPoint =
        if (timestampMillis >= previous.timestampMillis) this
        else copy(timestampMillis = previous.timestampMillis)
}

data class HandwritingWritingArea(
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f && height > 0f) { "Writing area dimensions must be positive" }
    }
}

data class HandwritingModel(
    val requestedLanguageTag: String,
    val modelLanguageTag: String,
)

data class HandwritingCandidate(
    val text: String,
    val score: Float? = null,
) {
    init {
        require(text.isNotBlank()) { "A recognition candidate must contain text" }
    }
}
