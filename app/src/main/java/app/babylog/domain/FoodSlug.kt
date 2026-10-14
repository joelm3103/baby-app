package app.babylog.domain

import java.text.Normalizer

/**
 * Food slugs: the stable, never-displayed id a solids entry references.
 *
 * A slug rather than the display name because renaming "Courgette" to "Zucchini" must not orphan every
 * meal that referenced the food. It is derived deterministically from the name, so the seeded
 * catalogue, a custom entry, and a duplicate the parent types all land on the same id without a
 * lookup table.
 *
 * Accents fold to ASCII (`puree` for `purée`) so the JSON asset can use human spelling and still
 * produce the id the picker searches for. A name with no ASCII letters or digits — punctuation, an
 * emoji, a non-Latin script — yields an empty slug; the caller decides how to identify that food
 * rather than this function inventing a random id, which is not testable.
 */
fun foodSlug(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

/**
 * A slug not already in [taken], so adding a second food that slugs to the same id cannot silently
 * overwrite the first. Returns "" when the name produces no slug at all — the caller falls back to a
 * generated id rather than this function guessing.
 */
fun uniqueFoodSlug(name: String, taken: Set<String>): String {
    val base = foodSlug(name)
    if (base.isEmpty()) return ""
    if (base !in taken) return base
    var n = 2
    while ("$base-$n" in taken) n++
    return "$base-$n"
}
