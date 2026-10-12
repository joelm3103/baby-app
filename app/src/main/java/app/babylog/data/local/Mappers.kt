package app.babylog.data.local

import app.babylog.domain.BabyEvent
import app.babylog.domain.Child
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.FoodReaction
import app.babylog.domain.MealSlot
import app.babylog.domain.Sex
import app.babylog.domain.decodeFoodIds
import app.babylog.domain.encodeFoodIds
import java.time.LocalDate

/**
 * Entity <-> domain mapping, kept out of the entities so it can be unit-tested on the JVM with no
 * database, no Robolectric and no Android runtime.
 *
 * Unknown enum values are handled asymmetrically and deliberately:
 *  - **Event fields tolerate the unknown** (`enumOrNull`). This is the cross-version case: the wife's
 *    phone may be a newer build and write a type or reaction this build has never heard of. Dropping
 *    or nulling that row beats crashing the app she is holding at 3am.
 *  - **`Child.sex` does not tolerate it** (`valueOf`). Sex selects which percentile table to plot, and
 *    silently defaulting would draw a confidently wrong chart. Better to fail loudly on our own data.
 */
private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

// ---- Child ------------------------------------------------------------

fun ChildEntity.toDomain(): Child = Child(
    id = id,
    name = name,
    birthDate = LocalDate.ofEpochDay(birthDateEpochDay),
    sex = Sex.valueOf(sex),
    gestationalAgeDays = gestationalAgeDays,
    useAdjustedAge = useAdjustedAge,
    sortOrder = sortOrder,
)

fun Child.toEntity(now: Long): ChildEntity = ChildEntity(
    id = id,
    name = name,
    birthDateEpochDay = birthDate.toEpochDay(),
    gestationalAgeDays = gestationalAgeDays,
    sex = sex.name,
    useAdjustedAge = useAdjustedAge,
    sortOrder = sortOrder,
    deleted = false,
    updatedAt = now,
)

// ---- Event ------------------------------------------------------------

fun EventEntity.toDomain(): BabyEvent? {
    val eventType = enumOrNull<EventType>(type) ?: return null
    return BabyEvent(
        id = id,
        childId = childId,
        type = eventType,
        startedAt = startedAt,
        endedAt = endedAt,
        amountMl = amountMl,
        formulaBrand = formulaBrand,
        mealSlot = enumOrNull<MealSlot>(mealSlot),
        foodIds = decodeFoodIds(foodIdsCsv),
        reaction = enumOrNull<FoodReaction>(reaction),
        diaperKind = enumOrNull<DiaperKind>(diaperKind),
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
        deleted = deleted,
    )
}

fun List<EventEntity>.toDomain(): List<BabyEvent> = mapNotNull { it.toDomain() }

fun BabyEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    childId = childId,
    type = type.name,
    startedAt = startedAt,
    endedAt = endedAt,
    amountMl = amountMl,
    formulaBrand = formulaBrand,
    mealSlot = mealSlot?.name,
    foodIdsCsv = encodeFoodIds(foodIds),
    reaction = reaction?.name,
    diaperKind = diaperKind?.name,
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
    deleted = deleted,
)
