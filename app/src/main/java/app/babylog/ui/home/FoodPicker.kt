package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.babylog.data.local.FoodEntity
import app.babylog.domain.EventType
import app.babylog.domain.displayFoodName
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme
import kotlinx.coroutines.launch

/**
 * Search the solids catalogue, multi-select, and add a food the catalogue is missing.
 *
 * Foods the parent has already used sort to the top (the repository's observed ordering), so the
 * common case is one tap on a food that is already on screen. Filtering happens in memory over the
 * loaded list — ~90 rows is nothing, and it avoids a database query on every keystroke.
 *
 * The catalogue itself is not this screen's to change (ADR-0002): a rename here is LOCAL to this phone and
 * writes a proposal for the owner, and a catalogue entry can only be *hidden*, never deleted. A food this
 * phone added is genuinely this phone's, so it can be renamed properly and deleted.
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
    onRename: suspend (String, String) -> Unit,
    onHide: suspend (String, Boolean) -> Unit,
    onDelete: suspend (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FoodEntity?>(null) }
    var deleting by remember { mutableStateOf<FoodEntity?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val trimmed = query.trim()

    val visible = remember(foods) { foods.filterNot { it.hidden } }
    val hiddenFoods = remember(foods) { foods.filter { it.hidden } }
    val shown = if (showHidden) foods else visible
    val filtered = remember(shown, trimmed) {
        if (trimmed.isBlank()) {
            shown
        } else {
            shown.filter { displayFoodName(it.name, it.localName).contains(trimmed, ignoreCase = true) }
        }
    }
    val alreadyExists = foods.any {
        displayFoodName(it.name, it.localName).equals(trimmed, ignoreCase = true)
    }

    ActivitySheet(
        type = EventType.SOLIDS,
        title = "Foods",
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

        // Only when there is something to come back to: hiding a food would otherwise be a one-way door,
        // because nothing else in the app shows a hidden row.
        if (hiddenFoods.isNotEmpty()) {
            InputRow(
                label = if (hiddenFoods.size == 1) "1 hidden food" else "${hiddenFoods.size} hidden foods",
                onClick = {
                    haptics.toggle()
                    showHidden = !showHidden
                },
                chevron = false,
            ) {
                InputValue(if (showHidden) "Hide" else "Show")
            }
        }

        filtered.forEach { food ->
            val selected = food.id in selectedIds
            InputRow(
                label = displayFoodName(food.name, food.localName),
                onClick = {
                    if (food.hidden) {
                        haptics.toggle()
                        scope.launch { onHide(food.id, false) }
                    } else {
                        onToggle(food.id)
                    }
                },
                chevron = false,
            ) {
                when {
                    food.hidden -> InputValue("Hidden")
                    selected -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = accent)
                    food.custom -> InputValue("Custom")
                }
                FoodRowMenu(
                    food = food,
                    onRename = { renaming = it },
                    onHide = { target, hide ->
                        haptics.toggle()
                        scope.launch { onHide(target.id, hide) }
                    },
                    onDelete = { deleting = it },
                )
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
                        query = displayFoodName(food.name, food.localName)
                    }
                },
                chevron = false,
            ) {
                InputValue("New food")
            }
        }
    }

    renaming?.let { target ->
        RenameFoodDialog(
            food = target,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { onRename(target.id, name) }
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        haptics.confirm()
                        scope.launch { onDelete(target.id) }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            title = { Text("Delete this food?") },
            text = {
                Text(
                    "It disappears from the list on this phone and on the other one. Meals already " +
                        "logged with it keep their place in the log.",
                )
            },
        )
    }
}

/**
 * What can be done to one row.
 *
 * A catalogue entry offers **Hide**, never Delete: the catalogue belongs to both phones and to the owner,
 * so taking one parent's line out of the list is as far as a phone may go. A food this phone added is its
 * own, so it can be renamed for real and removed.
 */
@Composable
private fun FoodRowMenu(
    food: FoodEntity,
    onRename: (FoodEntity) -> Unit,
    onHide: (FoodEntity, Boolean) -> Unit,
    onDelete: (FoodEntity) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = BabyLogTheme.colors
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Actions for ${displayFoodName(food.name, food.localName)}",
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    open = false
                    onRename(food)
                },
            )
            DropdownMenuItem(
                text = { Text(if (food.hidden) "Show in the list" else "Hide from the list") },
                onClick = {
                    open = false
                    onHide(food, !food.hidden)
                },
            )
            if (food.custom) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        open = false
                        onDelete(food)
                    },
                )
            }
        }
    }
}

/**
 * Rename, with the honest sentence underneath.
 *
 * The two kinds mean different things and the dialog says which one the parent is doing, because "rename"
 * on a catalogue entry only changes THIS phone's picker — the owner has to publish it for the other phone
 * to agree, and the proposal is what tells them.
 */
@Composable
private fun RenameFoodDialog(food: FoodEntity, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(food.id) { mutableStateOf(displayFoodName(food.name, food.localName)) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = trimmed.isNotEmpty()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Rename food") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFieldRow(
                    label = "Name",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Callaloo",
                )
                Text(
                    text = if (food.custom) {
                        "This food was added on a phone, so the new name reaches the other phone too."
                    } else {
                        "This only changes the name on this phone. The shared catalogue stays as it is, " +
                            "and your rename is recorded as a suggestion for the owner."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BabyLogTheme.colors.textSecondary,
                )
            }
        },
    )
}
