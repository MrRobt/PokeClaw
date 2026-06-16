// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * A fully-resolved dynamic form definition
 * (US-D-029-AIGC-TEMPLATE-PARAMS-FORM).
 *
 * Produced by [ParamsFormParser.fromJson] from the cloud `paramsSchema`
 * blob, or constructed directly by callers (and tests) via
 * [ParamsFormBuilder]. Rendered by `ParamsFormRenderer` and validated
 * by `ParamsFormValidator`.
 */
data class ParamsForm(
    val fields: List<ParamsFormField>,
) {
    init {
        require(fields.distinctBy { it.key }.size == fields.size) {
            "ParamsForm fields must have unique keys; got duplicates: " +
                fields.groupBy { it.key }.filter { it.value.size > 1 }.keys
        }
    }

    /** Look up a field by its key. */
    fun fieldFor(key: String): ParamsFormField? = fields.firstOrNull { it.key == key }
}

/**
 * Tiny builder to construct a [ParamsForm] without having to repeat
 * the `key = ..., label = ..., required = ...` argument names. Each
 * `ParamsFormField` constructor takes the same three common params.
 */
class ParamsFormBuilder {
    private val fields = ArrayList<ParamsFormField>()

    fun text(key: String, label: String, required: Boolean = false, maxLength: Int? = null) = apply {
        fields += ParamsFormField.Text(key, label, required, maxLength)
    }

    fun textarea(key: String, label: String, required: Boolean = false, maxLength: Int? = null) = apply {
        fields += ParamsFormField.Textarea(key, label, required, maxLength)
    }

    fun enumChoice(key: String, label: String, options: List<String>, required: Boolean = false) = apply {
        fields += ParamsFormField.EnumChoice(key, label, required, options)
    }

    fun intNumber(key: String, label: String, required: Boolean = false, min: Int? = null, max: Int? = null) = apply {
        fields += ParamsFormField.IntNumber(key, label, required, min, max)
    }

    fun image(key: String, label: String, required: Boolean = false) = apply {
        fields += ParamsFormField.ImageSingle(key, label, required)
    }

    fun imageArray(key: String, label: String, required: Boolean = false, min: Int? = null, max: Int? = null) = apply {
        fields += ParamsFormField.ImageArray(key, label, required, min, max)
    }

    fun build(): ParamsForm = ParamsForm(fields.toList())
}

fun paramsForm(builder: ParamsFormBuilder.() -> Unit): ParamsForm =
    ParamsFormBuilder().apply(builder).build()
