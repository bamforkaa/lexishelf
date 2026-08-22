package com.example.localvocabulary.dictionary.provider.cccedict

import java.io.BufferedReader

internal class CcCedictParser {
    fun parse(reader: BufferedReader): CcCedictParseReport {
        val records = mutableListOf<CcCedictRecord>()
        val issues = mutableListOf<CcCedictParseIssue>()
        var blankLineCount = 0
        var commentLineCount = 0
        var lineNumber = 0

        reader.forEachLine { rawLine ->
            lineNumber += 1
            val line = rawLine.trim()
            when {
                line.isEmpty() -> blankLineCount += 1
                line.startsWith("#") -> commentLineCount += 1
                else -> parseRecord(line, lineNumber).fold(
                    onSuccess = records::add,
                    onFailure = { error ->
                        issues += CcCedictParseIssue(
                            lineNumber = lineNumber,
                            line = rawLine,
                            reason = error.message ?: "Malformed CC-CEDICT record",
                        )
                    },
                )
            }
        }

        return CcCedictParseReport(
            records = records,
            blankLineCount = blankLineCount,
            commentLineCount = commentLineCount,
            issues = issues,
        )
    }

    private fun parseRecord(line: String, lineNumber: Int): Result<CcCedictRecord> = runCatching {
        val firstSeparator = line.indexOfFirst(Char::isWhitespace)
        require(firstSeparator > 0) { "Traditional headword is missing" }
        val traditional = line.substring(0, firstSeparator)

        val simplifiedStart = line.indexOfFirstFrom(firstSeparator) { !it.isWhitespace() }
        require(simplifiedStart >= 0) { "Simplified headword is missing" }
        val simplifiedEnd = line.indexOfFirstFrom(simplifiedStart, Char::isWhitespace)
        require(simplifiedEnd > simplifiedStart) { "Pinyin and definitions are missing" }
        val simplified = line.substring(simplifiedStart, simplifiedEnd)

        val remainderStart = line.indexOfFirstFrom(simplifiedEnd) { !it.isWhitespace() }
        require(remainderStart >= 0) { "Pinyin is missing" }
        val remainder = line.substring(remainderStart)
        val isV2 = remainder.startsWith("[[")
        require(isV2 || remainder.startsWith("[")) { "Pinyin must start with '[' or '[[" }
        val pinyinEndToken = if (isV2) "]]" else "]"
        val pinyinStart = if (isV2) 2 else 1
        val pinyinEnd = remainder.indexOf(pinyinEndToken, startIndex = pinyinStart)
        require(pinyinEnd >= pinyinStart) { "Pinyin closing bracket is missing" }
        val pinyin = remainder.substring(pinyinStart, pinyinEnd).trim()
        require(pinyin.isNotEmpty()) { "Pinyin is empty" }

        val definitionBlock = remainder.substring(pinyinEnd + pinyinEndToken.length).trim()
        require(definitionBlock.length >= 3) { "Definition block is missing" }
        require(definitionBlock.startsWith('/') && definitionBlock.endsWith('/')) {
            "Definitions must be enclosed by '/'"
        }
        val definitionBody = definitionBlock.substring(1, definitionBlock.lastIndex)
        val rawSenses = definitionBody.split('/')
        require(rawSenses.isNotEmpty() && rawSenses.none(String::isBlank)) {
            "Definition contains an empty sense"
        }
        val senses = rawSenses.map { rawSense ->
            rawSense.split(';').map(String::trim).also { glosses ->
                require(glosses.none(String::isBlank)) {
                    "Definition contains an empty gloss"
                }
            }
        }

        CcCedictRecord(
            traditional = traditional,
            simplified = simplified,
            pinyin = pinyin,
            senses = senses,
            format = if (isV2) CcCedictFormat.V2 else CcCedictFormat.V1,
            sourceLineNumber = lineNumber,
        )
    }

    private fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (index in startIndex until length) {
            if (predicate(this[index])) return index
        }
        return -1
    }
}
