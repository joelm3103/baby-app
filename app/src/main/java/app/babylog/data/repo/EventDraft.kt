package app.babylog.data.repo

import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperColor
import app.babylog.domain.DiaperConsistency
import app.babylog.domain.DiaperKind
import app.babylog.domain.DoseUnit
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
    /** Empty unless the nappy is DIRTY; the sheet clears them when Dirty is off, before the save. */
    val diaperColors: List<DiaperColor> = emptyList(),
    val diaperConsistency: List<DiaperConsistency> = emptyList(),
    val weightGrams: Int? = null,
    val lengthMm: Int? = null,
    val headMm: Int? = null,
    val temperatureC: Double? = null,
    val medication: String? = null,
    val doseAmount: Double? = null,
    val doseUnit: DoseUnit? = null,
    val vaccineName: String? = null,
    val note: String? = null,
) {
    fun toEvent(
        id: String,
        childId: String,
        createdBy: String,
        updatedAt: Long,
        deleted: Boolean = false,
    ): BabyEvent = BabyEvent(
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
        diaperColors = diaperColors,
        diaperConsistency = diaperConsistency,
        weightGrams = weightGrams,
        lengthMm = lengthMm,
        headMm = headMm,
        temperatureC = temperatureC,
        medication = medication,
        doseAmount = doseAmount,
        doseUnit = doseUnit,
        vaccineName = vaccineName,
        note = note,
        createdBy = createdBy,
        updatedAt = updatedAt,
        deleted = deleted,
    )

    /**
     * The EDIT path: this draft's values written onto an event that already exists.
     *
     * Identity is the point. The row keeps its `id`, its `childId` and its `createdBy`, so an edit is an
     * edit rather than a delete-and-reinsert — which matters beyond tidiness, because sync (Phase 13)
     * propagates by id and a re-inserted row would arrive as a brand-new event on the other phone.
     *
     * Deliberately implemented as [toEvent] with the identity filled in from [existing] rather than as
     * a second field-by-field copy: a duplicated field list is how a new column silently fails to
     * survive an edit.
     *
     * [existing]'s child is NOT taken from the draft. In Both mode one tap writes two rows, and editing
     * First Twin's feed must never rewrite Second Twin's.
     */
    fun applyTo(existing: BabyEvent, updatedAt: Long): BabyEvent {
        require(type == existing.type) {
            "a draft edits its own activity type: ${existing.type} cannot become $type"
        }
        return toEvent(
            id = existing.id,
            childId = existing.childId,
            createdBy = existing.createdBy,
            updatedAt = updatedAt,
            deleted = existing.deleted,
        )
    }
}
