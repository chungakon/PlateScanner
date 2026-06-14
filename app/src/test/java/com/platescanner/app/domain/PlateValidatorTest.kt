package com.platescanner.app.domain

import com.platescanner.app.domain.PlateValidator.PlateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlateValidator].
 *
 * Covers:
 *  - 5 normalize cases (punctuation stripping + shape match)
 *  - 5 isValid cases (positive and negative shape checks)
 *  - 3 typeOf cases (one per supported family)
 */
class PlateValidatorTest {

    // ---------------- normalize ----------------

    @Test
    fun normalize_strips_middle_dot_and_uppercases() {
        // LLM sometimes inserts a "·" between the province and the rest.
        // Canonical form is 1 中文 + 1 字母 + 5 字符 = 7 chars total.
        assertEquals("粤A12345", PlateValidator.normalize("粤A·12345"))
    }

    @Test
    fun normalize_strips_spaces() {
        // Whitespace between every character is common in OCR output.
        assertEquals("京A12345", PlateValidator.normalize("京 A 12345"))
    }

    @Test
    fun normalize_uppercases_lowercase_letters() {
        // The middle dot is not the only offender — raw "a" survives.
        assertEquals("京A12345", PlateValidator.normalize("京a12345"))
    }

    @Test
    fun normalize_strips_dot_period_and_underscore_and_dash() {
        // Multiple punctuation styles in one input: "沪B-88_888"
        //  → strip '-', '_'  → "沪B88888" (沪 + B + 5 digits, 7 chars total).
        assertEquals("沪B88888", PlateValidator.normalize("沪B-88_888"))
    }

    @Test
    fun normalize_empty_returns_null() {
        // Empty / blank inputs must never produce a "valid" plate.
        assertNull(PlateValidator.normalize(""))
        assertNull(PlateValidator.normalize("   "))
    }

    // ---------------- isValid ----------------

    @Test
    fun isValid_accepts_blue_plate_with_police_suffix() {
        // Standard blue plate
        assertTrue(PlateValidator.isValid("京A12345"))
        // 警 suffix
        assertTrue(PlateValidator.isValid("京A1234警"))
        // 挂 suffix
        assertTrue(PlateValidator.isValid("粤B8888挂"))
    }

    @Test
    fun isValid_accepts_blue_plate_with_six_tail() {
        // Real-world regression: vision models (M3, GPT-4o) emit the
        // 1-中 + 1-字母 + 6-字符 form on plates where the body is 6 chars
        // instead of 5 (e.g. 粤TDH8884). Both are valid per the GB spec —
        // the old validator was rejecting this shape and silently dropping
        // every successful recognition from the model.
        assertTrue(PlateValidator.isValid("粤TDH8884"))
        assertTrue(PlateValidator.isValid("沪B123456"))
        assertTrue(PlateValidator.isValid("京AXYZ123"))
    }

    @Test
    fun isValid_accepts_green_new_energy_plate() {
        // 8-char new-energy plate; second char must be A-D or F.
        assertTrue(PlateValidator.isValid("京AD12345"))
        assertTrue(PlateValidator.isValid("沪BF00001"))
    }

    @Test
    fun isValid_accepts_hk_macau_plates() {
        // Pure ASCII, no Chinese — 2-3 letters + 2-5 alphanumeric chars.
        assertTrue(PlateValidator.isValid("AB1234"))
        assertTrue(PlateValidator.isValid("AA12345"))
        assertTrue(PlateValidator.isValid("ABC1234"))
    }

    @Test
    fun isValid_rejects_too_short() {
        // 6 chars — no Chinese plate is 6 chars long.
        assertFalse(PlateValidator.isValid("京A1234"))
    }

    @Test
    fun isValid_rejects_too_long_or_garbage() {
        // 9 chars, or pure Chinese, or random noise.
        assertFalse(PlateValidator.isValid("京A1234567"))
        assertFalse(PlateValidator.isValid("中国车牌"))
        assertFalse(PlateValidator.isValid(""))
    }

    // ---------------- typeOf ----------------

    @Test
    fun typeOf_blue_yellow_white() {
        assertEquals(
            PlateType.BLUE_YELLOW_WHITE,
            PlateValidator.typeOf("京A12345"),
        )
        assertEquals(
            PlateType.BLUE_YELLOW_WHITE,
            PlateValidator.typeOf("粤B8888挂"),
        )
    }

    @Test
    fun typeOf_green_new_energy() {
        assertEquals(
            PlateType.GREEN_NEW_ENERGY,
            PlateValidator.typeOf("京AD12345"),
        )
        // F is also a valid 2nd char for NE plates (per spec).
        assertEquals(
            PlateType.GREEN_NEW_ENERGY,
            PlateValidator.typeOf("沪BF00001"),
        )
    }

    @Test
    fun typeOf_hk_macau() {
        assertEquals(
            PlateType.HK_MACAU,
            PlateValidator.typeOf("AB1234"),
        )
        assertNotNull(PlateValidator.typeOf("AA12345"))
    }
}
