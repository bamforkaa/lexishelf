package com.example.localvocabulary.core.common

import java.util.UUID

fun interface StableIdGenerator {
    fun newId(): String
}

object UuidStableIdGenerator : StableIdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
