package com.callcontrol.domain

/** Result of trying to normalize a phone number to E.164. */
sealed interface NormalizeResult {
    data class Valid(val e164: String) : NormalizeResult
    data class Invalid(val reason: InvalidReason) : NormalizeResult
}

enum class InvalidReason {
    EMPTY,
    ILLEGAL_CHARACTERS,
    INVALID_LENGTH,
    INVALID_EGYPT_NUMBER,
    UNRECOGNIZED_FORMAT,
}

/**
 * Normalizes phone numbers to a single canonical E.164 form so that the same person
 * cannot be stored twice under different spellings.
 *
 * Egyptian formats that all map to the same value (example mobile):
 *  - `010xxxxxxxx`        (local, trunk prefix 0)
 *  - `+2010xxxxxxxx`      (international with +)
 *  - `002010xxxxxxxx`     (international with 00)
 *  - `2010xxxxxxxx`       (country code without + or 00; accepted only if it is a valid Egyptian number)
 *  - `+20 010xxxxxxxx`    (common mistake: trunk 0 kept after +20)
 *
 * Important: `0010xxxxxxxx` is NOT Egyptian. `00` means "international prefix", so it is read as
 * `+10xxxxxxxx`, a different number from `+2010xxxxxxxx`.
 *
 * Not supported on purpose: a bare mobile without the leading 0 (`10xxxxxxxx`). It is ambiguous
 * with other countries, so it is reported as [InvalidReason.UNRECOGNIZED_FORMAT] instead of guessed.
 */
object PhoneNormalizer {

    private val EGYPT_MOBILE = Regex("^1[0125]\\d{8}$")
    private val EGYPT_LANDLINE = Regex("^[2-9]\\d{7,8}$")
    private val CLEANED = Regex("^\\+?\\d+$")

    private const val MIN_INTERNATIONAL_DIGITS = 7
    private const val MAX_INTERNATIONAL_DIGITS = 15 // ITU-T E.164 limit

    /** Characters ignored when typing/pasting numbers (spaces, dashes, dots, parentheses, bidi marks). */
    private fun isSeparator(c: Char): Boolean =
        c == ' ' || c == '-' || c == '.' || c == '(' || c == ')' ||
            c == ' ' || c == '‎' || c == '‏' ||
            c in '‪'..'‮' || c in '⁦'..'⁩'

    /** Converts Arabic-Indic and Persian digits to ASCII and drops separators. */
    private fun clean(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when {
                isSeparator(c) -> Unit
                c in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                c in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun normalize(raw: String?): NormalizeResult {
        if (raw == null || raw.isBlank()) return NormalizeResult.Invalid(InvalidReason.EMPTY)

        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return NormalizeResult.Invalid(InvalidReason.EMPTY)
        if (!CLEANED.matches(cleaned)) return NormalizeResult.Invalid(InvalidReason.ILLEGAL_CHARACTERS)

        val hasPlus = cleaned.startsWith("+")
        val digits = if (hasPlus) cleaned.substring(1) else cleaned

        return when {
            hasPlus -> normalizeInternational(digits)
            digits.startsWith("00") -> normalizeInternational(digits.substring(2))
            digits.startsWith("0") -> normalizeEgyptNational(digits.substring(1))
            digits.startsWith("20") ->
                // Country code without + / 00. Only accepted when it is a valid Egyptian number.
                when (val r = normalizeInternational(digits)) {
                    is NormalizeResult.Valid -> r
                    is NormalizeResult.Invalid -> NormalizeResult.Invalid(InvalidReason.UNRECOGNIZED_FORMAT)
                }
            else -> NormalizeResult.Invalid(InvalidReason.UNRECOGNIZED_FORMAT)
        }
    }

    /**
     * Key used to look up an *incoming* call in the rule table.
     *
     * Returns the E.164 form when the number is valid. Otherwise (e.g. an operator short code such as
     * `16000`) it falls back to the cleaned digits, so that a short code still matches itself.
     * Returns null when there is nothing usable (null, blank, or illegal characters).
     */
    fun matchKey(raw: String?): String? {
        if (raw == null || raw.isBlank()) return null
        return when (val r = normalize(raw)) {
            is NormalizeResult.Valid -> r.e164
            is NormalizeResult.Invalid -> clean(raw).takeIf { it.isNotEmpty() && CLEANED.matches(it) }
        }
    }

    /** [digits] has no leading '+' or '00'; it starts with the country code. */
    private fun normalizeInternational(digits: String): NormalizeResult {
        if (digits.isEmpty() || digits.startsWith("0")) {
            return NormalizeResult.Invalid(InvalidReason.INVALID_LENGTH)
        }
        if (digits.startsWith("20")) {
            val national = digits.substring(2)
            // Tolerate the trunk 0 kept after the country code: +20 010...
            val candidates = if (national.startsWith("0")) listOf(national, national.substring(1)) else listOf(national)
            for (c in candidates) {
                if (isEgyptNational(c)) return NormalizeResult.Valid("+20$c")
            }
            return NormalizeResult.Invalid(InvalidReason.INVALID_EGYPT_NUMBER)
        }
        if (digits.length !in MIN_INTERNATIONAL_DIGITS..MAX_INTERNATIONAL_DIGITS) {
            return NormalizeResult.Invalid(InvalidReason.INVALID_LENGTH)
        }
        return NormalizeResult.Valid("+$digits")
    }

    /** [national] is the number after the Egyptian trunk prefix 0 was removed. */
    private fun normalizeEgyptNational(national: String): NormalizeResult =
        if (isEgyptNational(national)) NormalizeResult.Valid("+20$national")
        else NormalizeResult.Invalid(InvalidReason.INVALID_EGYPT_NUMBER)

    private fun isEgyptNational(national: String): Boolean =
        EGYPT_MOBILE.matches(national) || EGYPT_LANDLINE.matches(national)
}
