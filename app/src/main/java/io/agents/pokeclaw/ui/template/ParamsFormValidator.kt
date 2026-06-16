// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * Validates a key/value map against a [ParamsForm]
 * (US-D-029-AIGC-TEMPLATE-PARAMS-FORM).
 *
 * Returns a [Result] describing which fields (if any) failed validation,
 * with field-specific error keys the UI can highlight. The validator is
 * intentionally pure Kotlin so it can be unit-tested without Android UI.
 */
object ParamsFormValidator {

    /** Per-field error code, used by the UI to drive inline messages. */
    enum class Error {
        REQUIRED_MISSING,
        NOT_A_STRING,
        EMPTY_TEXT_REQUIRED,
        TOO_LONG,
        ENUM_VALUE_NOT_ALLOWED,
        NOT_AN_INT,
        INT_BELOW_MIN,
        INT_ABOVE_MAX,
        NOT_AN_IMAGE,
        NOT_AN_IMAGE_LIST,
        IMAGE_LIST_BELOW_MIN,
        IMAGE_LIST_ABOVE_MAX,
    }

    data class Issue(
        val fieldKey: String,
        val error: Error,
    )

    data class Result(
        val issues: List<Issue>,
    ) {
        val isValid: Boolean get() = issues.isEmpty()
        fun errorsFor(key: String): List<Error> =
            issues.filter { it.fieldKey == key }.map { it.error }
    }

    fun validate(form: ParamsForm, values: Map<String, Any?>): Result {
        val issues = ArrayList<Issue>()
        for (field in form.fields) {
            val raw = values[field.key]
            validateField(field, raw)?.let { issues += it }
        }
        return Result(issues)
    }

    private fun validateField(field: ParamsFormField, raw: Any?): Issue? = when (field) {
        is ParamsFormField.Text -> validateText(field, raw)
        is ParamsFormField.Textarea -> validateTextarea(field, raw)
        is ParamsFormField.EnumChoice -> validateEnum(field, raw)
        is ParamsFormField.IntNumber -> validateInt(field, raw)
        is ParamsFormField.ImageSingle -> validateImageSingle(field, raw)
        is ParamsFormField.ImageArray -> validateImageArray(field, raw)
    }

    private fun validateText(f: ParamsFormField.Text, raw: Any?): Issue? {
        val s = (raw as? String)?.trim().orEmpty()
        if (s.isEmpty()) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING)
            else if (f.maxLength != null && f.maxLength == 0) Issue(f.key, Error.EMPTY_TEXT_REQUIRED)
            else null
        }
        if (f.maxLength != null && s.length > f.maxLength) return Issue(f.key, Error.TOO_LONG)
        return null
    }

    private fun validateTextarea(f: ParamsFormField.Textarea, raw: Any?): Issue? {
        val s = (raw as? String)?.trim().orEmpty()
        if (s.isEmpty()) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING) else null
        }
        if (f.maxLength != null && s.length > f.maxLength) return Issue(f.key, Error.TOO_LONG)
        return null
    }

    private fun validateEnum(f: ParamsFormField.EnumChoice, raw: Any?): Issue? {
        val s = (raw as? String)?.trim().orEmpty()
        if (s.isEmpty()) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING) else null
        }
        if (s !in f.options) return Issue(f.key, Error.ENUM_VALUE_NOT_ALLOWED)
        return null
    }

    private fun validateInt(f: ParamsFormField.IntNumber, raw: Any?): Issue? {
        if (raw == null) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING) else null
        }
        val n: Int = when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: return Issue(f.key, Error.NOT_AN_INT)
            else -> return Issue(f.key, Error.NOT_AN_INT)
        }
        if (f.min != null && n < f.min) return Issue(f.key, Error.INT_BELOW_MIN)
        if (f.max != null && n > f.max) return Issue(f.key, Error.INT_ABOVE_MAX)
        return null
    }

    private fun validateImageSingle(f: ParamsFormField.ImageSingle, raw: Any?): Issue? {
        val present = when (raw) {
            is String -> raw.isNotBlank()
            is List<*> -> raw.isNotEmpty()
            null -> false
            else -> true
        }
        if (!present) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING) else null
        }
        // Any non-empty representation is accepted (URI string or list
        // with at least one item). A stricter content-type check would
        // require platform-specific URIs.
        return null
    }

    private fun validateImageArray(f: ParamsFormField.ImageArray, raw: Any?): Issue? {
        val list: List<*> = when (raw) {
            is List<*> -> raw
            null -> emptyList<Any>()
            else -> return Issue(f.key, Error.NOT_AN_IMAGE_LIST)
        }
        if (list.isEmpty()) {
            return if (f.required) Issue(f.key, Error.REQUIRED_MISSING) else null
        }
        if (f.min != null && list.size < f.min) return Issue(f.key, Error.IMAGE_LIST_BELOW_MIN)
        if (f.max != null && list.size > f.max) return Issue(f.key, Error.IMAGE_LIST_ABOVE_MAX)
        return null
    }
}
