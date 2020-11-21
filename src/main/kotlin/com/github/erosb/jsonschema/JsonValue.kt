package com.github.erosb.jsonschema

data class JsonParseException(override val message: String, val location: TextLocation) : RuntimeException()

data class DocumentSource(val filePath: String?)

data class JsonPointer(val segments: List<String>)

fun pointer(vararg segments: String) = JsonPointer(segments.toList())

open class TextLocation(internal val lineNumber: Int, internal val position: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextLocation

        if (lineNumber != other.lineNumber) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lineNumber
        result = 31 * result + position
        return result
    }
    
    override fun toString(): String {
        return "line ${lineNumber}, character ${position}"
    }
}

open class SourceLocation(lineNumber: Int,
                          position: Int,
                          val pointer: JsonPointer,
                          val documentSource: DocumentSource? = null): TextLocation(lineNumber, position) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as SourceLocation

        if (lineNumber != other.lineNumber) return false
        if (position != other.position) return false
        if (pointer != other.pointer) return false
        if (documentSource != other.documentSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + pointer.hashCode()
        result = 31 * result + (documentSource?.hashCode() ?: 0)
        result = 31 * result + lineNumber
        result = 31 * result + position
        return result
    }

    override fun toString(): String {
        return "line ${lineNumber}, character ${position}, pointer: ${pointer}"
    }
}

object UnknownSource : SourceLocation(0, 0, JsonPointer(emptyList())) {
    override fun toString(): String = "UNKNOWN"
}

interface IJsonValue {
    val location: SourceLocation
    fun requireString(): JsonValue
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

open class JsonValue(override val location: SourceLocation = UnknownSource): IJsonValue {
    override fun requireString(): JsonValue = TODO("Not yet implemented")
}

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
