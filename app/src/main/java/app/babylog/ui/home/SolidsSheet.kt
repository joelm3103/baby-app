package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.babylog.data.local.FoodEntity
import app.babylog.data.repo.EventDraft
import app.babylog.domain.EventType
import app.babylog.domain.FoodReaction
import app.babylog.domain.MealSlot
import java.time.ZoneId

/**
 * Solids: meal slot, foods from the catalogue, a meal-level reaction, notes.
 *
 * The reaction is recorded **per meal**, not per food, which is what the schema already stores
 * (`BabyEvent.reaction`). A per-food flag would need a join table and a migration; the documented
 * escape hatch is in `domain/FoodIds.kt`, and it is not worth paying for until the distinction is
 * actually needed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SolidsSheet(
    accent: Color,
    zone: ZoneId,
    now: Long,
    foods: List<FoodEntity>,
    onAddFood: suspend (String) -> FoodEntity,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    var startedAt by remember { mutableStateOf(now) }
    var mealSlot by remember { mutableStateOf<MealSlot?>(null) }
    var reaction by remember { mutableStateOf<FoodReaction?>(null) }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    ActivitySheet(
        title = "Solids",
        accent = accent,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.SOLIDS,
                    startedAt = startedAt,
                    mealSlot = mealSlot,
                    foodIds = selectedIds,
                    reaction = reaction,
                    note = note.trim().ifEmpty { null },
                ),
            )
        },
    ) {
        TimeRow(instantMs = startedAt, zone = zone, onChange = { startedAt = it })

        ChipRow(
            label = "Meal",
            options = MealSlot.entries,
            selected = mealSlot,
            accent = accent,
            labelOf = { slot -> slot.name.lowercase().replaceFirstChar { it.uppercase() } },
            onSelect = { mealSlot = it },
        )

        InputRow(label = "Foods", onClick = { showPicker = true }) {
            InputValue(if (selectedIds.isEmpty()) "Add" else "${selectedIds.size} selected")
        }

        // What was picked, visible without reopening the picker. Tapping a chip removes that food.
        val chosen = selectedIds.mapNotNull { id -> foods.firstOrNull { it.id == id } }
        if (chosen.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chosen.forEach { food ->
                    ChoiceChip(
                        label = food.name,
                        selected = true,
                        accent = accent,
                        onClick = { selectedIds = selectedIds - food.id },
                    )
                }
            }
        }

        ChipRow(
            label = "Reaction",
            options = FoodReaction.entries,
            selected = reaction,
            accent = accent,
            labelOf = { if (it == FoodReaction.LOVED_IT) "Loved it" else "Allergy or sensitivity" },
            onSelect = { reaction = it },
        )

        TextFieldRow(
            label = "Notes",
            value = note,
            onValueChange = { note = it },
            placeholder = "Optional",
            singleLine = false,
        )
    }

    if (showPicker) {
        FoodPickerScreen(
            accent = accent,
            foods = foods,
            selectedIds = selectedIds,
            onToggle = { id ->
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            },
            onAddFood = onAddFood,
            onClose = { showPicker = false },
        )
    }
}
