package app.babylog.domain

/**
 * A food, as one source describes it.
 *
 * Three sources can make a claim about the same id, and they are not equal: the bundled asset is a floor,
 * a published catalogue is the owner's correction, and a pending document is a parent's addition.
 */
data class CatalogueClaim(val name: String, val category: String)

/**
 * What THIS phone thinks about a food, which is never a fact about the catalogue.
 *
 * `localName` is one parent renaming a catalogue entry for their own picker, and `hidden` is them taking it
 * out of it. Both stay on the device: the other phone curates its own list, and a proposal document is how
 * a disagreement becomes visible rather than silent.
 */
data class LocalFoodOpinion(
    val localName: String?,
    val hidden: Boolean,
    val lastUsedAt: Long?,
)

/** One id, settled: the row the database should hold. */
data class FoodResolution(
    val id: String,
    val name: String,
    val category: String,
    /** True while the food is a parent's addition rather than a catalogue entry. */
    val custom: Boolean,
    val localName: String?,
    val hidden: Boolean,
    val lastUsedAt: Long?,
)

/**
 * Who wins one id — the food equivalent of [resolveSync], and pure for the same reason: it is the only
 * place a disagreement is settled, so it has to be provable without Room or Firestore.
 *
 * The order is deliberate and each line carries a rule:
 *
 * - **`published` beats `seeded`.** That is what makes a console edit reach both phones without an APK;
 *   if the asset won, a rename could never travel.
 * - **`pending` is the last resort**, so an id the catalogue publishes stops being "custom" the moment it
 *   is published. The row is kept rather than deleted, because meals already reference its id.
 * - **`localName` / `hidden` win for display and are carried, never decided here.** They are one parent's
 *   opinion about their own picker, not a fact about the catalogue.
 * - **An id no source knows is not created.** Null, not an invented row — the same stance
 *   `foodFromSyncMap` takes, because a food with no name is not something to write into a database.
 */
fun resolveFood(
    id: String,
    seeded: CatalogueClaim? = null,
    published: CatalogueClaim? = null,
    pending: CatalogueClaim? = null,
    local: LocalFoodOpinion? = null,
): FoodResolution? {
    val winner = published ?: seeded ?: pending ?: return null
    // Both catalogues are "not the parent's addition". Only a pending entry is; and one that the catalogue
    // has since published is no longer pending — the catalogue speaks for it now.
    val custom = published == null && seeded == null
    return FoodResolution(
        id = id,
        name = winner.name,
        category = winner.category,
        custom = custom,
        localName = local?.localName,
        hidden = local?.hidden ?: false,
        lastUsedAt = local?.lastUsedAt,
    )
}

/**
 * The name a parent should read for a row: their own rename when they made one, otherwise the catalogue's.
 *
 * One function so the picker, the entry rows and an exported meal cannot disagree about what a food is
 * called on this phone.
 */
fun displayFoodName(name: String, localName: String?): String =
    localName?.takeIf { it.isNotBlank() } ?: name
