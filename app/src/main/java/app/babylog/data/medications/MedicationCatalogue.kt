package app.babylog.data.medications

import org.json.JSONObject

/**
 * The bundled list of medicine NAMES offered by the health sheet's medication picker.
 *
 * Names only, on purpose, and this is a safety decision rather than a scoping one. A dose for an infant
 * depends on weight and age, changes as they grow, and differs between formulations of the same drug; an
 * app that offered "the usual dose" would be producing a confident, plausible, wrong number that a tired
 * parent could read as advice. So the list answers "which medicine was it" and the parent answers "how
 * much" — every time, from the bottle in their hand.
 *
 * The brands in brackets are examples of what a name is sold as, not a recommendation of a brand, and
 * they differ by country. The provenance of the list itself is in the asset's own `note` field: it has
 * not been clinically reviewed.
 */
object MedicationCatalogueParser {
    /** Packaged under the app's assets, so it ships inside the APK and needs no network. */
    const val ASSET_PATH = "medications/medications.json"

    /** Display labels, in the asset's own order — the order is curated, not alphabetical. */
    fun parse(json: String): List<String> {
        val array = JSONObject(json).getJSONArray("medications")
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val name = array.optString(i).trim()
                if (name.isNotEmpty()) add(name)
            }
        }.distinct()
    }
}
