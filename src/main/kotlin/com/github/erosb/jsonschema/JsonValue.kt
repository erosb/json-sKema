package com.github.erosb.jsonschema

data class JsonParseException(override val message: String, val location: SourceLocation) : RuntimeException()

data class DocumentSource(val filePath: String?)

open class SourceLocation(val lineNumber: Int, val position: Int, val documentSource: DocumentSource? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourceLocation) return false

        if (lineNumber != other.lineNumber) return false
        if (position != other.position) return false
        if (documentSource != other.documentSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lineNumber
        result = 31 * result + position
        result = 31 * result + (documentSource?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "line ${lineNumber}, character ${position}"
    }
}

object UnknownSource : SourceLocation(0, 0) {
    override fun toString(): String = "UNKNOWN"
}

interface IJsonValue {
    val location: SourceLocation
}

interface IJsonString: IJsonValue {
    val value: String
}

interface IJsonBoolean: IJsonValue {
    val value: Boolean
}

interface IJsonNumber: IJsonValue {
    val value: Number
}

interface IJsonNull: IJsonValue 

interface IJsonArray<T: IJsonValue>: IJsonValue {
    val elements: List<T>
}

interface IJsonObject<P: IJsonString, V: IJsonValue> {
    val properties: Map<P, V>
}

open class JsonValue(override val location: SourceLocation = UnknownSource): IJsonValue

data class JsonNull(override val location: SourceLocation = UnknownSource) : JsonValue(location), IJsonNull

data class JsonBoolean(
        override val value: Boolean,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonBoolean

data class JsonNumber(
        override val value: Number,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonNumber

data class JsonString(override val value: String, override val location: SourceLocation = UnknownSource) : JsonValue(location), IJsonString {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonString) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

data class JsonArray(
        override val elements: List<JsonValue>,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonArray<JsonValue>

data class JsonObject(
        override val properties: Map<JsonString, JsonValue>,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonObject<JsonString, JsonValue>
