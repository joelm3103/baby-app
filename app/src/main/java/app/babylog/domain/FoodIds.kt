package app.babylog.domain

/**
 * A solids entry references a few foods from the catalogue, stored as a comma-separated id list rather
 * than a many-to-many join table.
 *
 * Why: at most a handful of foods per meal, the ids are stable slugs, and the only queries are "what
 * was in this meal" and "recently used foods" — neither needs a join. A cross-ref table would add a DAO,
 * a relation query and a second sync entity for no capability. The ids are never shown to a user, so
 * this is not a denormalisation of anything displayed.
 *
 * If per-food attributes are ever needed (a reaction per food rather than per meal), that is an
 * additive migration to a join table — the documented escape hatch.
 */
private const val SEPARATOR = ","

fun encodeFoodIds(ids: List<String>): String? =
    ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString(SEPARATOR).ifEmpty { null }

fun decodeFoodIds(encoded: String?): List<String> =
    encoded?.split(SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
        ?: emptyList()
