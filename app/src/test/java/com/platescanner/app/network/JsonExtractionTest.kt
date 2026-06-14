package com.platescanner.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MiniMaxApiImpl.extractFirstJsonObject].
 *
 * The real call site uses an LLM whose response shape is not strictly
 * guaranteed — the parser must gracefully handle ```json fences, leading
 * prose, and trailing junk.
 */
class JsonExtractionTest {

    @Test
    fun extracts_plain_object() {
        val input = """{"plates": [{"plate": "京A12345", "confidence": 0.95}]}"""
        val out = MiniMaxApiImpl.extractFirstJsonObject(input)
        assertEquals(input, out)
    }

    @Test
    fun extracts_json_inside_fence() {
        val input = """
            ```json
            {"plates": [{"plate": "沪B88888", "confidence": 0.83}]}
            ```
        """.trimIndent()
        val out = MiniMaxApiImpl.extractFirstJsonObject(input)
        assertNotNull(out)
        assertTrue(out!!.contains("沪B88888"))
        assertTrue(out.contains("confidence"))
    }

    @Test
    fun extracts_with_leading_prose() {
        val input = """这是结果: {"plates": [{"plate": "粤C99999", "confidence": 0.5}]}，以上。"""
        val out = MiniMaxApiImpl.extractFirstJsonObject(input)
        assertNotNull(out)
        assertTrue(out!!.contains("粤C99999"))
    }

    @Test
    fun handles_no_json() {
        assertNull(MiniMaxApiImpl.extractFirstJsonObject("没有车牌"))
    }

    @Test
    fun handles_empty_string() {
        assertNull(MiniMaxApiImpl.extractFirstJsonObject(""))
    }
}
