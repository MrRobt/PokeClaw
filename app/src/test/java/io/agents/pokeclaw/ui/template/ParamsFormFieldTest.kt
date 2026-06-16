// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ParamsFormField.fromMap] — exercises the sealed-class
 * factory used by [ParamsFormParser] without ever touching
 * `org.json.JSONObject` (US-D-029-AIGC-TEMPLATE-PARAMS-FORM).
 */
class ParamsFormFieldTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    // ---- Discriminator handling -----------------------------------------------

    @Test
    fun fromMap_missingKey_returnsNull() {
        assertNull(ParamsFormField.fromMap(mapOf("type" to "text")))
    }

    @Test
    fun fromMap_blankKey_returnsNull() {
        assertNull(ParamsFormField.fromMap(mapOf("type" to "text", "key" to "   ")))
    }

    @Test
    fun fromMap_unknownType_returnsNull() {
        assertNull(
            ParamsFormField.fromMap(mapOf("type" to "weird_type", "key" to "k"))
        )
    }

    @Test
    fun fromMap_missingType_returnsNull() {
        assertNull(ParamsFormField.fromMap(mapOf("key" to "k")))
    }

    @Test
    fun fromMap_typeIsCaseInsensitive() {
        val f = ParamsFormField.fromMap(mapOf("type" to "TEXT", "key" to "k"))
        assertTrue(f is ParamsFormField.Text)
    }

    // ---- Text ----------------------------------------------------------------

    @Test
    fun fromMap_text_usesMaxLength() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "maxLength" to 99),
        )
        assertTrue(f is ParamsFormField.Text)
        assertEquals(99, (f as ParamsFormField.Text).maxLength)
    }

    @Test
    fun fromMap_text_maxLengthMissing_isNull() {
        val f = ParamsFormField.fromMap(mapOf("type" to "text", "key" to "k"))
        assertTrue(f is ParamsFormField.Text)
        assertNull((f as ParamsFormField.Text).maxLength)
    }

    @Test
    fun fromMap_text_requiredStringTrue() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "required" to "TRUE"),
        )
        assertTrue((f as ParamsFormField.Text).required)
    }

    @Test
    fun fromMap_text_requiredNumberOne_isTrue() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "required" to 1),
        )
        assertTrue((f as ParamsFormField.Text).required)
    }

    @Test
    fun fromMap_text_requiredNumberZero_isFalse() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "required" to 0),
        )
        assertEquals(false, (f as ParamsFormField.Text).required)
    }

    @Test
    fun fromMap_text_labelFallsBackToKeyWhenBlank() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "label" to "  "),
        )
        assertEquals("k", (f as ParamsFormField.Text).label)
    }

    @Test
    fun fromMap_text_labelFallsBackToKeyWhenMissing() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k"),
        )
        assertEquals("k", (f as ParamsFormField.Text).label)
    }

    @Test
    fun fromMap_text_labelTrimmed() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "text", "key" to "k", "label" to "  标题  "),
        )
        assertEquals("标题", (f as ParamsFormField.Text).label)
    }

    @Test
    fun fromMap_text_defaultRequiredFalse() {
        val f = ParamsFormField.fromMap(mapOf("type" to "text", "key" to "k"))
        assertEquals(false, (f as ParamsFormField.Text).required)
    }

    // ---- Textarea ------------------------------------------------------------

    @Test
    fun fromMap_textarea_usesMaxLength() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "textarea", "key" to "k", "maxLength" to 200),
        )
        assertTrue(f is ParamsFormField.Textarea)
        assertEquals(200, (f as ParamsFormField.Textarea).maxLength)
    }

    // ---- Enum ----------------------------------------------------------------

    @Test
    fun fromMap_enum_optionsUsed() {
        val f = ParamsFormField.fromMap(
            mapOf(
                "type" to "enum", "key" to "k",
                "options" to listOf("a", "b", "c"),
            ),
        )
        assertTrue(f is ParamsFormField.EnumChoice)
        assertEquals(
            listOf("a", "b", "c"),
            (f as ParamsFormField.EnumChoice).options,
        )
    }

    @Test
    fun fromMap_enum_nonStringOptionsFiltered() {
        val f = ParamsFormField.fromMap(
            mapOf(
                "type" to "enum", "key" to "k",
                "options" to listOf("a", 7, "c", null),
            ),
        )
        assertTrue(f is ParamsFormField.EnumChoice)
        assertEquals(
            listOf("a", "c"),
            (f as ParamsFormField.EnumChoice).options,
        )
    }

    @Test
    fun fromMap_enum_missingOptions_isEmpty() {
        val f = ParamsFormField.fromMap(mapOf("type" to "enum", "key" to "k"))
        assertTrue(f is ParamsFormField.EnumChoice)
        assertEquals(emptyList<String>(), (f as ParamsFormField.EnumChoice).options)
    }

    // ---- Int -----------------------------------------------------------------

    @Test
    fun fromMap_int_boundsUsed() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "int", "key" to "k", "min" to 1, "max" to 99),
        )
        assertTrue(f is ParamsFormField.IntNumber)
        val int = f as ParamsFormField.IntNumber
        assertEquals(1, int.min)
        assertEquals(99, int.max)
    }

    @Test
    fun fromMap_int_boundsMissing_isNull() {
        val f = ParamsFormField.fromMap(mapOf("type" to "int", "key" to "k"))
        assertTrue(f is ParamsFormField.IntNumber)
        val int = f as ParamsFormField.IntNumber
        assertNull(int.min)
        assertNull(int.max)
    }

    // ---- Image ---------------------------------------------------------------

    @Test
    fun fromMap_imageSingle() {
        val f = ParamsFormField.fromMap(
            mapOf("type" to "image", "key" to "k", "required" to true),
        )
        assertTrue(f is ParamsFormField.ImageSingle)
        assertEquals(true, (f as ParamsFormField.ImageSingle).required)
    }

    @Test
    fun fromMap_imageArray() {
        val f = ParamsFormField.fromMap(
            mapOf(
                "type" to "image[]", "key" to "k",
                "min" to 1, "max" to 4, "required" to true,
            ),
        )
        assertTrue(f is ParamsFormField.ImageArray)
        val arr = f as ParamsFormField.ImageArray
        assertEquals(1, arr.min)
        assertEquals(4, arr.max)
        assertEquals(true, arr.required)
    }

    // ---- paramsForm / ParamsFormBuilder / ParamsForm ------------------------

    @Test
    fun paramsForm_builderProducesForm() {
        val form = paramsForm {
            text("a", "A")
            intNumber("b", "B", min = 0, max = 5)
        }
        assertEquals(2, form.fields.size)
        assertEquals("a", form.fieldFor("a")?.key)
        assertEquals("b", form.fieldFor("b")?.key)
    }

    @Test
    fun paramsForm_fieldForUnknownKey_returnsNull() {
        val form = paramsForm { text("a", "A") }
        assertNull(form.fieldFor("nope"))
    }

    @Test
    fun paramsForm_duplicateKeys_throws() {
        try {
            paramsForm {
                text("a", "A")
                text("a", "A again")
            }
            org.junit.Assert.fail("expected IllegalArgumentException for duplicate keys")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("unique keys"))
        }
    }

    @Test
    fun builder_appliesAllTypes() {
        val form = ParamsFormBuilder()
            .text("t1", "T1", true, 10)
            .textarea("t2", "T2", false, null)
            .enumChoice("t3", "T3", listOf("a"), true)
            .intNumber("t4", "T4", true, 0, 100)
            .image("t5", "T5", true)
            .imageArray("t6", "T6", false, 1, 3)
            .build()
        assertEquals(6, form.fields.size)
        assertTrue(form.fieldFor("t1") is ParamsFormField.Text)
        assertTrue(form.fieldFor("t2") is ParamsFormField.Textarea)
        assertTrue(form.fieldFor("t3") is ParamsFormField.EnumChoice)
        assertTrue(form.fieldFor("t4") is ParamsFormField.IntNumber)
        assertTrue(form.fieldFor("t5") is ParamsFormField.ImageSingle)
        assertTrue(form.fieldFor("t6") is ParamsFormField.ImageArray)
    }
}
