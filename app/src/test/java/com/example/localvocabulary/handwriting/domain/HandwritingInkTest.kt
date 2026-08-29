package com.example.localvocabulary.handwriting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingInkTest {
    @Test
    fun `stroke begin move and end preserve ordered points`() {
        val ink = HandwritingInk()
            .begin(point(1f, 2f, 20))
            .append(point(2f, 3f, 10))
            .end(point(3f, 4f, 30))

        assertEquals(1, ink.strokes.size)
        assertEquals(listOf(20L, 20L, 30L), ink.strokes.single().points.map { it.timestampMillis })
        assertTrue(ink.activePoints.isEmpty())
    }

    @Test
    fun `multiple strokes undo and clear are safe`() {
        val twoStrokes = HandwritingInk()
            .begin(point(1f, 1f, 1)).end()
            .begin(point(2f, 2f, 2)).end()

        assertEquals(2, twoStrokes.strokes.size)
        assertEquals(1, twoStrokes.undoLastStroke().strokes.size)
        assertTrue(twoStrokes.clear().isEmpty)
        assertTrue(HandwritingInk().undoLastStroke().isEmpty)
        assertTrue(HandwritingInk().end().isEmpty)
    }

    @Test
    fun `beginning a new stroke safely completes an active stroke`() {
        val ink = HandwritingInk()
            .begin(point(1f, 1f, 1))
            .append(point(2f, 2f, 2))
            .begin(point(3f, 3f, 3))

        assertEquals(1, ink.strokes.size)
        assertEquals(2, ink.strokes.single().points.size)
        assertEquals(1, ink.activePoints.size)
    }

    @Test
    fun `scaling preserves strokes timestamps and transforms local coordinates`() {
        val ink = HandwritingInk()
            .begin(point(10f, 20f, 1))
            .end(point(30f, 40f, 2))
            .scaled(scaleX = 2f, scaleY = 0.5f)

        assertEquals(listOf(20f, 60f), ink.strokes.single().points.map { it.x })
        assertEquals(listOf(10f, 20f), ink.strokes.single().points.map { it.y })
        assertEquals(listOf(1L, 2L), ink.strokes.single().points.map { it.timestampMillis })
    }

    private fun point(x: Float, y: Float, timestamp: Long) =
        HandwritingPoint(x, y, timestamp)
}
