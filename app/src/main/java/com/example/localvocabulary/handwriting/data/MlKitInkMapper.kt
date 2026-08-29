package com.example.localvocabulary.handwriting.data

import com.example.localvocabulary.handwriting.domain.HandwritingInk
import com.google.mlkit.vision.digitalink.recognition.Ink

internal object MlKitInkMapper {
    fun map(ink: HandwritingInk): Ink {
        val builder = Ink.builder()
        ink.strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { point ->
                strokeBuilder.addPoint(
                    Ink.Point.create(point.x, point.y, point.timestampMillis),
                )
            }
            builder.addStroke(strokeBuilder.build())
        }
        return builder.build()
    }
}
