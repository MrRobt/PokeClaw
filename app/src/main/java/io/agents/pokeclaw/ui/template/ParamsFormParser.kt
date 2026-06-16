// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.template

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the cloud `paramsSchema` JSON blob into a [ParamsForm]
 * (US-D-029-AIGC-TEMPLATE-PARAMS-FORM).
 *
 * The expected JSON shape is:
 * ```
 * { "fields": [ { "key": "...", "type": "text|textarea|enum|int|image|image[]",
 *                  "label": "...", "required": true, ... } ] }
 * ```
 * Unknown `type` values are dropped (with a log line) so a future
 * server-side type does not break the client; the rest of the form
 * still renders. Missing / malformed JSON throws [ParamsFormParseException]
 * with a descriptive message.
 */
object ParamsFormParser {

    private const val TAG = "ParamsFormParser"

    /**
     * Parse [json] and return a [ParamsForm]. Throws
     * [ParamsFormParseException] on any structural problem.
     */
    @Throws(ParamsFormParseException::class)
    fun fromJson(json: String?): ParamsForm {
        if (json.isNullOrBlank()) {
            throw ParamsFormParseException("paramsSchema JSON is null or blank")
        }
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw ParamsFormParseException("paramsSchema is not valid JSON: ${e.message}", e)
        }
        val array = root.optJSONArray("fields")
            ?: throw ParamsFormParseException("paramsSchema is missing the 'fields' array")
        val fields = ArrayList<ParamsFormField>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val map = jsonObjectToMap(obj)
            val field = ParamsFormField.fromMap(map)
            if (field != null) {
                fields += field
            } else {
                io.agents.pokeclaw.utils.XLog.w(
                    TAG,
                    "dropping field at index $i (missing key or unknown type): $map"
                )
            }
        }
        return ParamsForm(fields)
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = unwrap(obj.opt(k))
        }
        return out
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> {
            val list = ArrayList<Any?>(value.length())
            for (i in 0 until value.length()) list += unwrap(value.opt(i))
            list
        }
        JSONObject.NULL -> null
        else -> value
    }
}

/** Thrown by [ParamsFormParser.fromJson] on malformed input. */
class ParamsFormParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
