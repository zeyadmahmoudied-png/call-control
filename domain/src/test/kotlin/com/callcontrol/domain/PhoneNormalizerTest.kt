package com.callcontrol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

    private fun e164(raw: String?): String {
        val r = PhoneNormalizer.normalize(raw)
        assertTrue("expected Valid for '$raw' but was $r", r is NormalizeResult.Valid)
        return (r as NormalizeResult.Valid).e164
    }

    private fun invalid(raw: String?): InvalidReason {
        val r = PhoneNormalizer.normalize(raw)
        assertTrue("expected Invalid for '$raw' but was $r", r is NormalizeResult.Invalid)
        return (r as NormalizeResult.Invalid).reason
    }

    // ---- the three formats required by the spec ----

    @Test
    fun threeEgyptianFormatsMapToTheSameE164() {
        assertEquals("+201012345678", e164("01012345678"))
        assertEquals("+201012345678", e164("+201012345678"))
        assertEquals("+201012345678", e164("00201012345678"))
    }

    @Test
    fun differentSpellingsCollapseToOneKey() {
        val keys = listOf("010 1234 5678", "+20 10 1234 5678", "0020-10-1234-5678", "(010) 1234-5678")
            .map { e164(it) }.toSet()
        assertEquals(setOf("+201012345678"), keys)
    }

    @Test
    fun doubleZeroTenIsNotEgyptian() {
        // 0010... means "international prefix + 10...", a different number from +2010...
        val other = e164("0010 1234 5678")
        assertEquals("+101234 5678".replace(" ", ""), other)
        assertNotEquals("+201012345678", other)
    }

    // ---- other Egyptian forms ----

    @Test
    fun allEgyptianMobilePrefixesAccepted() {
        assertEquals("+201012345678", e164("01012345678"))
        assertEquals("+201112345678", e164("01112345678"))
        assertEquals("+201212345678", e164("01212345678"))
        assertEquals("+201512345678", e164("01512345678"))
    }

    @Test
    fun trunkZeroKeptAfterCountryCodeIsTolerated() {
        assertEquals("+201012345678", e164("+20 010 1234 5678"))
        assertEquals("+201012345678", e164("+2001012345678"))
    }

    @Test
    fun countryCodeWithoutPlusIsAcceptedWhenValid() {
        assertEquals("+201012345678", e164("201012345678"))
    }

    @Test
    fun egyptianLandlines() {
        assertEquals("+20223456789", e164("02 2345 6789"))   // Cairo
        assertEquals("+2034567890", e164("03 456 7890"))     // Alexandria style (8-digit national)
    }

    @Test
    fun arabicIndicAndPersianDigitsAreConverted() {
        assertEquals("+201012345678", e164("٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("+201012345678", e164("۰۱۰۱۲۳۴۵۶۷۸"))
        assertEquals("+201012345678", e164("+٢٠١٠١٢٣٤٥٦٧٨"))
    }

    @Test
    fun bidiMarksAndNbspAreIgnored() {
        assertEquals("+201012345678", e164("‪+20 10 1234 5678‬"))
    }

    @Test
    fun normalizingTwiceChangesNothing() {
        val once = e164("010-1234-5678")
        assertEquals(once, e164(once))
    }

    // ---- invalid Egyptian numbers ----

    @Test
    fun invalidEgyptianNumbersAreRejected() {
        assertEquals(InvalidReason.INVALID_EGYPT_NUMBER, invalid("010123"))           // too short
        assertEquals(InvalidReason.INVALID_EGYPT_NUMBER, invalid("01312345678"))      // 013 is not a mobile prefix
        assertEquals(InvalidReason.INVALID_EGYPT_NUMBER, invalid("+20 131 2345 678")) // same, international
        assertEquals(InvalidReason.INVALID_EGYPT_NUMBER, invalid("0101234567890123")) // too long
    }

    @Test
    fun bareMobileWithoutLeadingZeroIsNotGuessed() {
        assertEquals(InvalidReason.UNRECOGNIZED_FORMAT, invalid("1012345678"))
    }

    @Test
    fun emptyAndGarbageInput() {
        assertEquals(InvalidReason.EMPTY, invalid(null))
        assertEquals(InvalidReason.EMPTY, invalid(""))
        assertEquals(InvalidReason.EMPTY, invalid("   "))
        assertEquals(InvalidReason.ILLEGAL_CHARACTERS, invalid("abc"))
        assertEquals(InvalidReason.ILLEGAL_CHARACTERS, invalid("010x1234567"))
        assertEquals(InvalidReason.ILLEGAL_CHARACTERS, invalid("++201012345678"))
        assertEquals(InvalidReason.ILLEGAL_CHARACTERS, invalid("0101234+5678"))
        assertEquals(InvalidReason.ILLEGAL_CHARACTERS, invalid("+")) // a lone plus has no digits
    }

    // ---- other countries ----

    @Test
    fun internationalNumbersKeepTheirOwnCountryCode() {
        assertEquals("+14155552671", e164("+1 (415) 555-2671"))
        assertEquals("+447911123456", e164("00447911123456"))
        assertEquals("+447911123456", e164("+44 7911 123456"))
    }

    @Test
    fun internationalLengthLimits() {
        assertEquals(InvalidReason.INVALID_LENGTH, invalid("+123"))
        assertEquals(InvalidReason.INVALID_LENGTH, invalid("+1234567890123456")) // 16 digits
        assertEquals("+123456789012345", e164("+123456789012345"))               // 15 digits is the maximum
    }

    // ---- matchKey (used for incoming calls) ----

    @Test
    fun matchKeyUsesE164ForValidNumbers() {
        assertEquals("+201012345678", PhoneNormalizer.matchKey("01012345678"))
        assertEquals("+201012345678", PhoneNormalizer.matchKey("+201012345678"))
    }

    @Test
    fun matchKeyFallsBackToCleanedDigitsForShortCodes() {
        assertEquals("16000", PhoneNormalizer.matchKey("16000"))
        assertEquals("16000", PhoneNormalizer.matchKey("1 6000"))
    }

    @Test
    fun matchKeyIsNullWhenNothingUsable() {
        assertNull(PhoneNormalizer.matchKey(null))
        assertNull(PhoneNormalizer.matchKey(""))
        assertNull(PhoneNormalizer.matchKey("  "))
        assertNull(PhoneNormalizer.matchKey("Unknown"))
    }
}
