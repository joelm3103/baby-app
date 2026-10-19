package app.babylog.domain

import java.time.LocalDate

/** 40 weeks. The reference against which prematurity is measured. */
const val TERM_GESTATION_DAYS = 280

enum class Sex { BOY, GIRL }

/**
 * A child. Birth details are DATA, never constants: both boys were born a date set in Settings at the premature range, but
 * the app must not hardcode either fact.
 *
 * Gestational age is stored in DAYS rather than weeks so the adjusted-age subtraction stays exact:
 * 210 days = the premature range. Storing "30" for weeks would make `280 - 30 = 250` look plausible while being
 * nonsense, which is exactly how a unit slip corrupts every percentile downstream.
 */
data class Child(
    val id: String,
    val name: String,
    val birthDate: LocalDate,
    val sex: Sex,
    val gestationalAgeDays: Int = TERM_GESTATION_DAYS,
    val useAdjustedAge: Boolean = true,
    val sortOrder: Int = 0,
) {
    val isPreterm: Boolean get() = gestationalAgeDays < TERM_GESTATION_DAYS
    val prematureDays: Int get() = (TERM_GESTATION_DAYS - gestationalAgeDays).coerceAtLeast(0)
    val gestationalWeeksLabel: String get() = "${gestationalAgeDays / 7} weeks"
}
