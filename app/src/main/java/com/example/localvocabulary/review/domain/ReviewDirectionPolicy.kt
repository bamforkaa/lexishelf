package com.example.localvocabulary.review.domain

/** Only scheduled questions affect balance; retries and resets are not new questions. */
object ReviewDirectionPolicy {
    fun next(stateId: String, events: List<ReviewEvent>): ReviewMode {
        val scheduled = events.filter {
            it.reviewStateId == stateId && it.kind == ReviewEventKind.SCHEDULED
        }
        val meaningFirst = scheduled.count { it.promptDirection == ReviewMode.MEANING_TO_EXPRESSION }
        val expressionFirst = scheduled.count { it.promptDirection == ReviewMode.EXPRESSION_TO_MEANING }
        val recent = scheduled.sortedWith(compareBy<ReviewEvent> { it.reviewedAt }.thenBy { it.stableId })
            .takeLast(2)
        val lastDirection = recent.lastOrNull()?.promptDirection
        return when {
            // Legacy history may be very uneven. Correct it without a long run of one direction.
            recent.size == 2 && lastDirection != null && recent.first().promptDirection == lastDirection ->
                lastDirection.opposite()
            meaningFirst < expressionFirst -> ReviewMode.MEANING_TO_EXPRESSION
            expressionFirst < meaningFirst -> ReviewMode.EXPRESSION_TO_MEANING
            lastDirection != null -> lastDirection.opposite()
            stateId.hashCode() and 1 == 0 -> ReviewMode.MEANING_TO_EXPRESSION
            else -> ReviewMode.EXPRESSION_TO_MEANING
        }
    }

    private fun ReviewMode.opposite() = when (this) {
        ReviewMode.MEANING_TO_EXPRESSION -> ReviewMode.EXPRESSION_TO_MEANING
        ReviewMode.EXPRESSION_TO_MEANING -> ReviewMode.MEANING_TO_EXPRESSION
    }
}
