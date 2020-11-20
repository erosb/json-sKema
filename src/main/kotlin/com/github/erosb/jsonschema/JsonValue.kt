package com.github.erosb.jsonschema

data class DocumentSource(val filePath: String?)

data class SourceLocation(val lineNumber: Int, val position: Int, val documentSource: DocumentSource? = null)

open class JsonValue

interface LocatedJsonValue {
    val location: SourceLocation
}

data class JsonParseException(override val message: String, val location: SourceLocation): RuntimeException()

open class JsonNull : JsonValue()

data class LocatedJsonNull(override val location: SourceLocation): JsonNull(), LocatedJsonValue

open class JsonString(open val value: String): JsonValue()

data class LocatedJsonString(
        override val value: String,
        override val location: SourceLocation
): JsonString(value), LocatedJsonValue

open class JsonArray(open val elements: List<JsonValue>): JsonValue()

data class LocatedJsonArray(
        override val elements: List<JsonValue>,
        override val location: SourceLocation
): JsonArray(elements), LocatedJsonValue

open class JsonObject(open val properties: Map<JsonString, JsonValue>): JsonValue()

data class LocatedJsonObject(
        override val properties: Map<JsonString, JsonValue>,
        override val location: SourceLocation
): JsonObject(properties), LocatedJsonValue

open class JsonBoolean(open val value: Boolean): JsonValue()

data class LocatedJsonBoolean(
        override val value: Boolean,
        override val location: SourceLocation
): JsonBoolean(value), LocatedJsonValue


open class JsonNumber(open val value: Number): JsonValue();

data class LocatedJsonNumber(
        override val value: Number,
        override val location: SourceLocation
): JsonNumber(value), LocatedJsonValue
