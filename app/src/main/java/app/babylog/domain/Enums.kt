package app.babylog.domain

/**
 * Tolerant enum lookup for values read back out of storage.
 *
 * A stored value that no longer maps to a constant (a downgrade, a hand-edited preference, a typo in
 * a key) must not crash the app. Callers pass an explicit default so the fallback is a decision
 * rather than an accident.
 */
inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
