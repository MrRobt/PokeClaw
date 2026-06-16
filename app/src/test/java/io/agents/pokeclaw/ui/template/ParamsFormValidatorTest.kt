// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ParamsFormValidator]
 * (US-D-029-AIGC-TEMPLATE-PARAMS-FORM). Builds forms via the
 * pure-Kotlin [paramsForm] DSL so the tests run on the JVM without
 * an `org.json.JSONObject` mock.
 */
class ParamsFormValidatorTest {

    @Before
    fun setUp() {
        io.agents.pokeclaw.utils.XLog.setTestMode(true)
    }

    @org.junit.After
    fun tearDown() {
        io.agents.pokeclaw.utils.XLog.setTestMode(false)
    }

    // ---- Sanity ---------------------------------------------------------------

    @Test
    fun emptyForm_emptyValues_isValid() {
        val form = paramsForm { }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertTrue(r.isValid)
        assertEquals(0, r.issues.size)
    }

    @Test
    fun emptyForm_ignoresUnknownKeys() {
        val form = paramsForm { }
        val r = ParamsFormValidator.validate(form, mapOf("stray" to "value"))
        assertTrue(r.isValid)
    }

    // ---- Text -----------------------------------------------------------------

    @Test
    fun text_requiredMissing() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = 100)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertEquals(
            listOf(ParamsFormValidator.Error.REQUIRED_MISSING),
            r.errorsFor("title"),
        )
    }

    @Test
    fun text_requiredBlankString_alsoMissing() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = 100)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("title" to "   "),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("title")
        )
    }

    @Test
    fun text_optionalMissing_isValid() {
        val form = paramsForm {
            text("title", "标题", required = false, maxLength = 100)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertTrue(r.isValid)
    }

    @Test
    fun text_optionalEmpty_isValid() {
        val form = paramsForm {
            text("title", "标题", required = false, maxLength = 100)
        }
        val r = ParamsFormValidator.validate(form, mapOf("title" to ""))
        assertTrue(r.isValid)
    }

    @Test
    fun text_maxLengthExceeded() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("title" to "abcdef"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.TOO_LONG in r.errorsFor("title")
        )
    }

    @Test
    fun text_maxLengthExact_isValid() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("title" to "abcde"),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun text_unlimitedMaxLength_acceptsAny() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = null)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("title" to "x".repeat(10_000)),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun text_valueTrimmedBeforeLengthCheck() {
        val form = paramsForm {
            text("title", "标题", required = true, maxLength = 5)
        }
        // "  abc  " -> trim -> "abc" (3 chars), should pass
        val r = ParamsFormValidator.validate(
            form, mapOf("title" to "  abc  "),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun text_zeroMaxLength_withEmptyValue_isInvalid() {
        // maxLength=0 with required=false: empty value triggers EMPTY_TEXT_REQUIRED
        val form = paramsForm {
            text("title", "标题", required = false, maxLength = 0)
        }
        val r = ParamsFormValidator.validate(form, mapOf("title" to ""))
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.EMPTY_TEXT_REQUIRED in r.errorsFor("title")
        )
    }

    // ---- Textarea -------------------------------------------------------------

    @Test
    fun textarea_requiredMissing() {
        val form = paramsForm {
            textarea("body", "正文", required = true, maxLength = 1000)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("body")
        )
    }

    @Test
    fun textarea_optionalEmpty_isValid() {
        val form = paramsForm {
            textarea("body", "正文", required = false, maxLength = 1000)
        }
        val r = ParamsFormValidator.validate(form, mapOf("body" to ""))
        assertTrue(r.isValid)
    }

    @Test
    fun textarea_maxLengthExceeded() {
        val form = paramsForm {
            textarea("body", "正文", required = true, maxLength = 3)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("body" to "hello"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.TOO_LONG in r.errorsFor("body")
        )
    }

    // ---- EnumChoice -----------------------------------------------------------

    @Test
    fun enum_requiredMissing() {
        val form = paramsForm {
            enumChoice("style", "风格", listOf("写实", "二次元"), required = true)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("style")
        )
    }

    @Test
    fun enum_optionalMissing_isValid() {
        val form = paramsForm {
            enumChoice("style", "风格", listOf("写实", "二次元"), required = false)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertTrue(r.isValid)
    }

    @Test
    fun enum_disallowedValue() {
        val form = paramsForm {
            enumChoice("style", "风格", listOf("写实", "二次元"), required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("style" to "水墨"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.ENUM_VALUE_NOT_ALLOWED in r.errorsFor("style")
        )
    }

    @Test
    fun enum_allowedValue_isValid() {
        val form = paramsForm {
            enumChoice("style", "风格", listOf("写实", "二次元"), required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("style" to "写实"),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun enum_emptyOptions_acceptsNothing() {
        val form = paramsForm {
            enumChoice("style", "风格", emptyList(), required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("style" to "anything"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.ENUM_VALUE_NOT_ALLOWED in r.errorsFor("style")
        )
    }

    // ---- IntNumber ------------------------------------------------------------

    @Test
    fun int_requiredMissing() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("count")
        )
    }

    @Test
    fun int_stringNumber_isValid() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to "5"),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_intValue_isValid() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 5),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_doubleValue_coerced() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 5.0),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_stringNotANumber() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to "five"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.NOT_AN_INT in r.errorsFor("count")
        )
    }

    @Test
    fun int_unparseableType() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to listOf("a")),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.NOT_AN_INT in r.errorsFor("count")
        )
    }

    @Test
    fun int_belowMin() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 0),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.INT_BELOW_MIN in r.errorsFor("count")
        )
    }

    @Test
    fun int_aboveMax() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 11),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.INT_ABOVE_MAX in r.errorsFor("count")
        )
    }

    @Test
    fun int_atMin_isValid() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 1),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_atMax_isValid() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to 10),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_unbounded_acceptsAny() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = null, max = null)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to -999_999),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun int_stringWithSpaces_trimmed() {
        val form = paramsForm {
            intNumber("count", "数量", required = true, min = 1, max = 10)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("count" to "  7  "),
        )
        assertTrue(r.isValid)
    }

    // ---- ImageSingle ----------------------------------------------------------

    @Test
    fun image_requiredMissing() {
        val form = paramsForm {
            image("ref", "参考图", required = true)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("ref")
        )
    }

    @Test
    fun image_optionalMissing_isValid() {
        val form = paramsForm {
            image("ref", "参考图", required = false)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertTrue(r.isValid)
    }

    @Test
    fun image_stringUri_isValid() {
        val form = paramsForm {
            image("ref", "参考图", required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("ref" to "content://media/123"),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun image_listWithOne_isValid() {
        val form = paramsForm {
            image("ref", "参考图", required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("ref" to listOf("content://media/123")),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun image_blankString_isMissing() {
        val form = paramsForm {
            image("ref", "参考图", required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("ref" to "   "),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("ref")
        )
    }

    @Test
    fun image_emptyList_isMissing() {
        val form = paramsForm {
            image("ref", "参考图", required = true)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("ref" to emptyList<String>()),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("ref")
        )
    }

    // ---- ImageArray -----------------------------------------------------------

    @Test
    fun imageArray_requiredMissing() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = 1, max = 5)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("refs")
        )
    }

    @Test
    fun imageArray_wrongType() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = 1, max = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to "single uri"),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.NOT_AN_IMAGE_LIST in r.errorsFor("refs")
        )
    }

    @Test
    fun imageArray_belowMin() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = 2, max = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to listOf("uri1")),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.IMAGE_LIST_BELOW_MIN in r.errorsFor("refs")
        )
    }

    @Test
    fun imageArray_aboveMax() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = 1, max = 2)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to listOf("a", "b", "c")),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.IMAGE_LIST_ABOVE_MAX in r.errorsFor("refs")
        )
    }

    @Test
    fun imageArray_inRange_isValid() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = 1, max = 3)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to listOf("a", "b")),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun imageArray_optionalEmpty_isValid() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = false, min = 0, max = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to emptyList<String>()),
        )
        assertTrue(r.isValid)
    }

    @Test
    fun imageArray_unbounded_acceptsAnyNonEmpty() {
        val form = paramsForm {
            imageArray("refs", "参考图组", required = true, min = null, max = null)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("refs" to listOf("a", "b", "c", "d")),
        )
        assertTrue(r.isValid)
    }

    // ---- Multiple fields / cross-cutting -------------------------------------

    @Test
    fun multipleFields_reportsEachIssueSeparately() {
        val form = paramsForm {
            text("a", "A", required = true, maxLength = 5)
            text("b", "B", required = true, maxLength = 5)
            intNumber("c", "C", required = true, min = 1, max = 5)
        }
        val r = ParamsFormValidator.validate(
            form, mapOf("a" to "toooooo", "c" to 0),
        )
        assertFalse(r.isValid)
        assertTrue(
            ParamsFormValidator.Error.TOO_LONG in r.errorsFor("a")
        )
        assertTrue(
            ParamsFormValidator.Error.REQUIRED_MISSING in r.errorsFor("b")
        )
        assertTrue(
            ParamsFormValidator.Error.INT_BELOW_MIN in r.errorsFor("c")
        )
    }

    @Test
    fun validResult_exposesAllFieldErrorsByKey() {
        val form = paramsForm {
            text("a", "A", required = true, maxLength = 5)
            intNumber("b", "B", required = true, min = 1, max = 5)
        }
        val r = ParamsFormValidator.validate(form, emptyMap())
        assertEquals(2, r.issues.size)
        assertNotNull(r.errorsFor("a"))
        assertNotNull(r.errorsFor("b"))
        // Unknown key returns an empty list (not null), still well-defined.
        assertTrue(r.errorsFor("nonexistent").isEmpty())
    }

    // ---- Result helpers -------------------------------------------------------

    @Test
    fun result_isValidReflectsIssues() {
        val form = paramsForm { text("a", "A", required = true) }
        val valid = ParamsFormValidator.validate(form, mapOf("a" to "ok"))
        val invalid = ParamsFormValidator.validate(form, mapOf("a" to ""))
        assertTrue(valid.isValid)
        assertFalse(invalid.isValid)
    }

    @Test
    fun issue_fieldKeyAndErrorExposed() {
        val form = paramsForm { text("a", "A", required = true) }
        val r = ParamsFormValidator.validate(form, emptyMap())
        val issue = r.issues.first()
        assertEquals("a", issue.fieldKey)
        assertEquals(
            ParamsFormValidator.Error.REQUIRED_MISSING, issue.error,
        )
    }
}
