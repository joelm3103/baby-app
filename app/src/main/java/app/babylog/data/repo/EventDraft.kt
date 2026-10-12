package app.babylog.data.repo

import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.FoodReaction
import app.babylog.domain.MealSlot

/**
 * "What is being logged", before it is attributed to a child and given an id.
 *
 * Keeping this separate from [BabyEvent] is what makes Both-mode logging trivial: one draft becomes
 * N events (one per child) with identical content and timestamps. If the draft were a [BabyEvent] we
 * would be tempted to write ONE row with a null childId, and per-child history would be wrong forever.
 */
data class EventDraft(
    val type: EventType,
    val startedAt: Long,
    val endedAt: Long? = null,
    val amountMl: Int? = null,
    val formulaBrand: String? = null,
    val mealSlot: MealSlot? = null,
    val foodIds: List<String> = emptyList(),
    val reaction: FoodReaction? = null,
    val diaperKind: DiaperKind? = null,
    val rash: Boolean? = null,
    val weightGrams: Int? = null,
    val lengthMm: Int? = null,
    val headMm: Int? = null,
    val temperatureC: Double? = null,
    val medication: String? = null,
    val vaccineName: String? = null,
    val note: String? = null,
) {
    fun toEvent(id: String, childId: String, createdBy: String, updatedAt: Long): BabyEvent = BabyEvent(
        id = id,
        childId = childId,
        type = type,
        startedAt = startedAt,
        endedAt = endedAt,
        amountMl = amountMl,
        formulaBrand = formulaBrand,
        mealSlot = mealSlot,
        foodIds = foodIds,
        reaction = reaction,
        diaperKind = diaperKind,
        rash = rash,
        weightGrams = weightGrams,
        lengthMm = lengthMm,
        headMm = headMm,
        temperatureC = temperatureC,
        medication = medication,
        vaccineName = vaccineName,
        note = note,
        createdBy = createdBy,
        updatedAt = updatedAt,
    )
}
