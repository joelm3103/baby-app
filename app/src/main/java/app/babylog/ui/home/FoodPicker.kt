package app.babylog.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.babylog.data.local.FoodEntity
import kotlinx.coroutines.launch

/**
 * Search the solids catalogue, multi-select, and add a food the catalogue is missing.
 *
 * Foods the parent has already used sort to the top (the repository's observed ordering), so the
 * common case is one tap on a food that is already on screen. Filtering happens in memory over the
 * loaded list — ~90 rows is nothing, and it avoids a database query on every keystroke.
 *
 * This is a nested full-screen surface rather than a section of the solids sheet, so system back closes
 * the *picker* first and only then the sheet behind it.
 */
@Composable
fun FoodPickerScreen(
    accent: Color,
    foods: List<FoodEntity>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onAddFood: suspend (String) -> FoodEntity,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val trimmed = query.trim()
    val filtered = remember(foods, trimmed) {
        if (trimmed.isBlank()) foods else foods.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
    val alreadyExists = foods.any { it.name.equals(trimmed, ignoreCase = true) }

    ActivitySheet(
        title = "Foods",
        accent = accent,
        saveLabel = "Done",
        onDismiss = onClose,
        onSave = onClose,
    ) {
        TextFieldRow(
            label = "Search",
            value = query,
            onValueChange = { query = it },
            placeholder = "Banana, yoghurt\u2026",
        )

        if (foods.isEmpty()) {
            InputRow(label = "Loading the food list\u2026") { }
        } else if (filtered.isEmpty()) {
            InputRow(label = "No match for \u201c$trimmed\u201d") { }
        }

        filtered.forEach { food ->
            val selected = food.id in selectedIds
            InputRow(label = food.name, onClick = { onToggle(food.id) }, chevron = false) {
                when {
                    selected -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = accent)
                    food.custom -> InputValue("Custom")
                }
            }
        }

        // Only off the end of the list, and only when the typed name is genuinely new — otherwise the
        // row would offer to create a duplicate of something already selected above it.
        if (trimmed.isNotEmpty() && !alreadyExists) {
            InputRow(
                label = "Add \u201c$trimmed\u201d",
                onClick = {
                    scope.launch {
                        val food = onAddFood(trimmed)
                        if (food.id !in selectedIds) onToggle(food.id)
                        // Keep the new food visible and ticked instead of clearing the search.
                        query = food.name
                    }
                },
                chevron = false,
            ) {
                InputValue("New food")
            }
        }
    }
}
