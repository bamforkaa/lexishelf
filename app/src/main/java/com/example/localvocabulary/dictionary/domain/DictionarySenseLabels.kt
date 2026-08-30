package com.example.localvocabulary.dictionary.domain

enum class DictionarySenseLabelCategory {
    REGISTER,
    TEMPORAL,
    GRAMMAR,
    REGION,
}

enum class DictionarySenseLabelType(
    val category: DictionarySenseLabelCategory,
    internal val order: Int,
) {
    FORMAL(DictionarySenseLabelCategory.REGISTER, 0),
    INFORMAL(DictionarySenseLabelCategory.REGISTER, 1),
    COLLOQUIAL(DictionarySenseLabelCategory.REGISTER, 2),
    SLANG(DictionarySenseLabelCategory.REGISTER, 3),
    VULGAR(DictionarySenseLabelCategory.REGISTER, 4),
    OFFENSIVE(DictionarySenseLabelCategory.REGISTER, 5),
    DEROGATORY(DictionarySenseLabelCategory.REGISTER, 6),
    LITERARY(DictionarySenseLabelCategory.REGISTER, 7),
    ARCHAIC(DictionarySenseLabelCategory.TEMPORAL, 8),
    OBSOLETE(DictionarySenseLabelCategory.TEMPORAL, 9),
    DATED(DictionarySenseLabelCategory.TEMPORAL, 10),
    RARE(DictionarySenseLabelCategory.TEMPORAL, 11),
    TRANSITIVE(DictionarySenseLabelCategory.GRAMMAR, 12),
    INTRANSITIVE(DictionarySenseLabelCategory.GRAMMAR, 13),
    COUNTABLE(DictionarySenseLabelCategory.GRAMMAR, 14),
    UNCOUNTABLE(DictionarySenseLabelCategory.GRAMMAR, 15),
    AUXILIARY(DictionarySenseLabelCategory.GRAMMAR, 16),
    IMPERSONAL(DictionarySenseLabelCategory.GRAMMAR, 17),
    REGIONAL(DictionarySenseLabelCategory.REGION, 18),
    DIALECTAL(DictionarySenseLabelCategory.REGION, 19),
}

data class DictionarySenseLabel(
    val type: DictionarySenseLabelType,
    val regionalDetail: String? = null,
) {
    init {
        require(regionalDetail == null || type.category == DictionarySenseLabelCategory.REGION) {
            "Only regional labels may retain a regional detail"
        }
        require(regionalDetail == null || regionalDetail.isNotBlank()) {
            "Regional label detail must not be blank"
        }
    }
}

fun Iterable<DictionarySenseLabel>.normalizedSenseLabels(): List<DictionarySenseLabel> =
    distinctBy { it.type to it.regionalDetail }
        .sortedWith(compareBy({ it.type.order }, { it.regionalDetail.orEmpty() }))
