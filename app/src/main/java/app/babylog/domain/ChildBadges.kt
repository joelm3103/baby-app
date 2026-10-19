package app.babylog.domain

import java.util.Locale

/**
 * Each child's badge: the first letter of the name, numbered when two or more children share one.
 *
 * The letter has always been the child's identifier in this app — a hue means one activity type and
 * nothing else, so a colour cannot tell children apart. That breaks the moment two names start with the
 * same letter: `Kai` and `Kim` both rendered `K`, so the Both-mode card, the Growth legend and the fever
 * table showed one mark for two children. Colliding letters are therefore numbered in display order:
 * `Kai`, `Kim` -> `K1`, `K2`, and a letter that occurs once stays a single letter.
 *
 * [children] must already be in display order — `ChildDao.observeAll()` returns `ORDER BY sortOrder, name`,
 * and that is the order the switcher shows. This function deliberately does not sort: two opinions about
 * order would number badges differently from the list they sit next to. The consequence is accepted: a
 * child inserted *earlier* in `sortOrder` renumbers the group it lands in.
 *
 * Collision is on the uppercased first letter, so `kai` and `Kai` collide by design. A blank name yields
 * `?` rather than an empty string, because an empty badge collapses the circle in `ChildBadge` to a dot.
 */
fun badgesFor(children: List<Child>): Map<String, String> {
    val letters = children.associate { child ->
        child.id to (child.name.trim().firstOrNull()?.uppercase(Locale.ENGLISH)?.toString() ?: "?")
    }
    val counts = letters.values.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return children.associate { child ->
        val letter = letters.getValue(child.id)
        val badge = if (counts.getValue(letter) == 1) {
            letter
        } else {
            val position = seen.merge(letter, 1, Int::plus)!!
            "$letter$position"
        }
        child.id to badge
    }
}
