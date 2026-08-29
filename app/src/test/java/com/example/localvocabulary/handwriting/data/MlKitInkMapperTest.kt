package com.example.localvocabulary.handwriting.data

import com.example.localvocabulary.handwriting.domain.HandwritingInk
import com.example.localvocabulary.handwriting.domain.HandwritingPoint
import com.example.localvocabulary.handwriting.domain.HandwritingStroke
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitInkMapperTest {
    @Test
    fun `ordered vector strokes map without rasterization or lost timestamps`() {
        val mapped = MlKitInkMapper.map(
            HandwritingInk(
                strokes = listOf(
                    HandwritingStroke(
                        listOf(
                            HandwritingPoint(1f, 2f, 10),
                            HandwritingPoint(3f, 4f, 20),
                        ),
                    ),
                    HandwritingStroke(listOf(HandwritingPoint(5f, 6f, 30))),
                ),
            ),
        )

        assertEquals(2, mapped.strokes.size)
        val firstPoints = mapped.strokes.first().pointsInGlobalCoordinates
        val lastPoint = mapped.strokes.last().pointsInGlobalCoordinates.single()
        assertEquals(listOf(10L, 20L), firstPoints.map { it.timestamp })
        assertEquals(5f, lastPoint.x)
        assertEquals(6f, lastPoint.y)
    }
}
