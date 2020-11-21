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

open class JsonValue(location: SourceLocation = UnknownSource) 

data class JsonNull(val location: SourceLocation = UnknownSource) : JsonValue(location)

data class JsonString(val value: String, val location: SourceLocation = UnknownSource) : JsonValue(location) {

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

data class JsonArray(val elements: List<JsonValue>, val location: SourceLocation = UnknownSource) : JsonValue(location)

data class JsonObject(open val properties: Map<JsonString, JsonValue>, val location: SourceLocation = UnknownSource) : JsonValue(location)

data class JsonBoolean(open val value: Boolean, val location: SourceLocation = UnknownSource) : JsonValue(location)

data class JsonNumber(open val value: Number, val location: SourceLocation = UnknownSource) : JsonValue(location)
