package app.babylog.data.local

import org.json.JSONObject

/**
 * The bundled solids catalogue: a curated list of first foods, shipped as a JSON asset so adding a
 * food is a data change rather than a code change.
 *
 * The `id` is a stable slug the meal references. A later asset release may only ADD foods — seeding is
 * insert-if-absent, so it can never rename or delete a food a logged meal already points at.
 */
data class FoodCatalogue(
    val version: String,
    val foods: List<FoodEntity>,
)

object FoodCatalogueParser {
    /** Packaged under the app's assets, so it ships inside the APK and needs no network. */
    const val ASSET_PATH = "foods/foods.json"

    fun parse(json: String): FoodCatalogue {
        val root = JSONObject(json)
        val array = root.getJSONArray("foods")
        val foods = buildList(array.length()) {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                add(
                    FoodEntity(
                        id = row.getString("id"),
                        name = row.getString("name"),
                        category = row.optString("category", "Other"),
                        lastUsedAt = null,
                        custom = false,
                    ),
                )
            }
        }
        return FoodCatalogue(version = root.optString("version"), foods = foods)
    }
}
