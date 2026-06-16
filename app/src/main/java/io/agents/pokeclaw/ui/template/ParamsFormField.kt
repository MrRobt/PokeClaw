// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

/**
 * A single dynamic form field defined by an AIGC template's
 * `paramsSchema` (US-D-029-AIGC-TEMPLATE-PARAMS-FORM).
 *
 * The sealed hierarchy maps 1:1 to the `type` discriminator values
 * produced by the dyq `aigc_template.paramsSchema` JSON:
 *   - "text"       → [Text]
 *   - "textarea"   → [Textarea]
 *   - "enum"       → [EnumChoice]
 *   - "int"        → [IntNumber]
 *   - "image"      → [ImageSingle]
 *   - "image[]"    → [ImageArray]
 *
 * Common properties (key, label, required) live on the base class; each
 * subtype adds the constraints specific to its widget.
 */
sealed class ParamsFormField(
    open val key: String,
    open val label: String,
    open val required: Boolean,
) {
    /** Single-line text. `maxLength` may be null for unlimited. */
    data class Text(
        override val key: String,
        override val label: String,
        override val required: Boolean,
        val maxLength: Int?,
    ) : ParamsFormField(key, label, required)

    /** Multi-line text. */
    data class Textarea(
        override val key: String,
        override val label: String,
        override val required: Boolean,
        val maxLength: Int?,
    ) : ParamsFormField(key, label, required)

    /** Pick one value from a fixed list of options. */
    data class EnumChoice(
        override val key: String,
        override val label: String,
        override val required: Boolean,
        val options: List<String>,
    ) : ParamsFormField(key, label, required)

    /** Integer number with optional [min] / [max] bounds. */
    data class IntNumber(
        override val key: String,
        override val label: String,
        override val required: Boolean,
        val min: Int?,
        val max: Int?,
    ) : ParamsFormField(key, label, required)

    /** Single image picker. */
    data class ImageSingle(
        override val key: String,
        override val label: String,
        override val required: Boolean,
    ) : ParamsFormField(key, label, required)

    /** Image array picker with optional [min] / [max] count bounds. */
    data class ImageArray(
        override val key: String,
        override val label: String,
        override val required: Boolean,
        val min: Int?,
        val max: Int?,
    ) : ParamsFormField(key, label, required)

    companion object {
        /**
         * Build a [ParamsFormField] from a [Map] (typically produced by
         * `org.json.JSONObject` in production). Returns null when the
         * `key` or `type` discriminator is missing.
         */
        fun fromMap(map: Map<String, Any?>): ParamsFormField? {
            val key = (map["key"] as? String)?.trim().orEmpty()
            if (key.isEmpty()) return null
            val label = (map["label"] as? String)?.trim().orEmpty().ifEmpty { key }
            val required = when (val r = map["required"]) {
                is Boolean -> r
                is String -> r.equals("true", ignoreCase = true)
                is Number -> r.toInt() != 0
                else -> false
            }
            return when (val t = (map["type"] as? String)?.lowercase()) {
                "text" -> Text(
                    key = key,
                    label = label,
                    required = required,
                    maxLength = (map["maxLength"] as? Number)?.toInt(),
                )
                "textarea" -> Textarea(
                    key = key,
                    label = label,
                    required = required,
                    maxLength = (map["maxLength"] as? Number)?.toInt(),
                )
                "enum" -> EnumChoice(
                    key = key,
                    label = label,
                    required = required,
                    options = (map["options"] as? List<*>)
                        ?.mapNotNull { (it as? String) }
                        ?: emptyList(),
                )
                "int" -> IntNumber(
                    key = key,
                    label = label,
                    required = required,
                    min = (map["min"] as? Number)?.toInt(),
                    max = (map["max"] as? Number)?.toInt(),
                )
                "image" -> ImageSingle(key, label, required)
                "image[]" -> ImageArray(
                    key = key,
                    label = label,
                    required = required,
                    min = (map["min"] as? Number)?.toInt(),
                    max = (map["max"] as? Number)?.toInt(),
                )
                else -> null
            }
        }
    }
}
