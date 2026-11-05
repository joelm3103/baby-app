package app.babylog.domain

import kotlin.math.roundToInt

/**
 * The amount field takes a SUM, not just a number (Phase 24).
 *
 * The reason is on the kitchen counter rather than in the code: a bottle is made to 6 fl oz, the baby
 * leaves 2, and what was drunk is 4 — arithmetic a tired parent should not have to do in their head at
 * 3am, and arithmetic this app should not make them do in two steps (log 6, edit to 4) either. So the
 * field accepts `6-2` and stores the result.
 *
 * Deliberately small: `+ - * /`, decimals, spaces, parentheses, and the two multiplication/division signs
 * a phone keyboard is likely to offer (`×` `÷`). No functions, no variables, no exponents, no percent —
 * a calculator that can express more than a bottle amount is a calculator that can express a wrong one.
 * Anything it cannot read is `null`, which the sheet treats as "not a number yet" rather than guessing.
 */
fun evaluateAmount(text: String): Double? {
    val parser = AmountParser(text)
    val value = parser.expression() ?: return null
    parser.skipSpaces()
    if (!parser.atEnd) return null
    return value.takeIf { it.isFinite() }
}

/**
 * The amount in canonical millilitres, or null when the text is not a readable amount.
 *
 * The one place that turns typed text into what is stored, so the sheet, the tests and the untouched
 * prefill rule cannot disagree about what "3" means in a phone set to fl oz.
 */
fun amountMlFor(text: String, unit: VolumeUnit): Int? =
    evaluateAmount(text)?.let { toMillilitres(it, unit).roundToInt() }

/**
 * What `=` puts back INTO the field: the sum's value in the display unit, at most one decimal.
 *
 * The result belongs in the field rather than on a line beneath it (owner, 2026-11-04): a parent who types
 * `7*2` wants to see `14` where they typed, check it, and save it — not to compare two numbers in two places.
 * Rounding here is the rounding the field would store, so what is shown is what is kept.
 *
 * Null when the text cannot be read, so the sheet can leave the half-typed sum alone and say why.
 */
fun resolvedAmountText(text: String, unit: VolumeUnit): String? {
    val value = evaluateAmount(text) ?: return null
    return formatVolume(toMillilitres(value, unit), unit).substringBefore(' ')
}

/** The characters the amount field accepts, in addition to digits and the decimal point. */
const val OPERATOR_CHARS = "+-*/×÷−()"

/**
 * Recursive descent over the string, with the standard precedence a parent expects: `2+3*2` is 8, not 10.
 *
 * Null means "this is not a sum", and every branch says so by returning null rather than by throwing.
 */
private class AmountParser(text: String) {
    private val source = text
    private var index = 0

    val atEnd: Boolean get() = index >= source.length

    fun skipSpaces() {
        while (!atEnd && source[index].isWhitespace()) index++
    }

    /** `+` and `-`, lowest precedence, left to right. */
    fun expression(): Double? {
        var value = term() ?: return null
        while (true) {
            skipSpaces()
            when (peek()) {
                '+' -> {
                    index++
                    value += term() ?: return null
                }

                '-', '−' -> {
                    index++
                    value -= term() ?: return null
                }

                else -> return value
            }
        }
    }

    /** `*` and `/`, above the sums. Division by zero is unreadable, not infinity. */
    private fun term(): Double? {
        var value = unary() ?: return null
        while (true) {
            skipSpaces()
            when (peek()) {
                '*', '×' -> {
                    index++
                    value *= unary() ?: return null
                }

                '/', '÷' -> {
                    index++
                    val divisor = unary() ?: return null
                    if (divisor == 0.0) return null
                    value /= divisor
                }

                else -> return value
            }
        }
    }

    /** A leading minus, so `-2+6` reads as 4 and a mistyped `6--2` is a mistake rather than a crash. */
    private fun unary(): Double? {
        skipSpaces()
        if (peek() == '-' || peek() == '−') {
            index++
            val value = unary() ?: return null
            return -value
        }
        return primary()
    }

    private fun primary(): Double? {
        skipSpaces()
        if (atEnd) return null
        if (peek() == '(') {
            index++
            val value = expression() ?: return null
            skipSpaces()
            if (peek() != ')') return null
            index++
            return value
        }
        return number()
    }

    /** Digits with at most one decimal point: `.5` and `6.` both read, `6..2` does not. */
    private fun number(): Double? {
        val start = index
        var digits = 0
        var points = 0
        while (!atEnd) {
            val char = source[index]
            if (char.isDigit()) {
                digits++
                index++
            } else if (char == '.' && points == 0) {
                points++
                index++
            } else {
                break
            }
        }
        if (digits == 0) {
            index = start
            return null
        }
        return source.substring(start, index).toDoubleOrNull()
    }

    private fun peek(): Char? = if (atEnd) null else source[index]
}
