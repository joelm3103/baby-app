package app.babylog.domain

/**
 * A coded value list stored as a comma-separated string, for the fields that are a small ordered set of
 * an enum's names — a diaper's colours and consistencies.
 *
 * The same reasoning as [encodeFoodIds]: at most a handful of values per record, the tokens are enum
 * names, and no query ever needs to join on them. A cross-ref table would add a DAO, a relation and a
 * second sync entity to answer a question nobody asks ("which nappies were green?" is not a feature).
 *
 * Two asymmetries are deliberate:
 *  - **Empty encodes to NULL**, not to `""`, so "not recorded" has exactly one representation in the
 *    database and in a sync document.
 *  - **Decoding DROPS an unknown name** rather than failing the row. The other phone may be a newer build
 *    with a colour this build has never heard of, and the honest response is to show the nappy without it
 *    rather than to hide the nappy.
 */
private const val SEPARATOR = ","

fun <T : Enum<T>> encodeCodes(values: List<T>): String? =
    values.distinct().joinToString(SEPARATOR) { it.name }.ifEmpty { null }

fun <T : Enum<T>> decodeCodes(encoded: String?, all: Array<T>): List<T> =
    encoded?.split(SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull { name -> all.firstOrNull { it.name == name } }
        ?.distinct()
        ?: emptyList()
