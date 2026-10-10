package com.demo.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Providers return numbers as strings, strings as numbers, nulls, and `[]` for "empty object". Be tolerant. */
internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.str(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> ""
}

internal fun JsonElement?.int(default: Int = 0): Int =
    str().trim().toDoubleOrNull()?.toInt() ?: default

internal fun JsonElement?.long(): Long? = str().trim().toDoubleOrNull()?.toLong()

/** An array, or a single object wrapped in a list, or empty for anything else. */
internal fun JsonElement?.items(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> if (isEmpty()) emptyList() else listOf(this)
    else -> emptyList()
}

internal fun JsonObject.s(key: String) = this[key].str()
