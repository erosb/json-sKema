package com.github.erosb.jsonschema

import java.lang.IllegalArgumentException
import java.net.URI
import java.util.stream.Collectors.joining

data class JsonParseException(override val message: String, val location: TextLocation) : RuntimeException()

data class JsonTypingException(val expectedType: String, val actualType: String, val location: SourceLocation) : RuntimeException() {
    override fun toString() = "${location.pointer}: expected ${expectedType}, found ${actualType} (line ${location.lineNumber}, position ${location.position})"
}

data class JsonPointer(val segments: List<String>) {
    override fun toString() = "#" + (if (segments.isEmpty()) "" else "/") + segments.stream().collect(joining("/"))
}

fun pointer(vararg segments: String) = JsonPointer(segments.toList())

open class TextLocation(val lineNumber: Int, val position: Int, val documentSource: URI? = null) {
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
                          documentSource: URI? = null) : TextLocation(lineNumber, position, documentSource) {
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

    internal fun trimPointerSegments(leadingSegmentsToBeRemoved: Int): SourceLocation {
        if (leadingSegmentsToBeRemoved > pointer.segments.size) {
            throw IllegalArgumentException("can not remove $leadingSegmentsToBeRemoved segment from pointer $pointer")
        }
        return SourceLocation(
            lineNumber, position, JsonPointer(pointer.segments.subList(leadingSegmentsToBeRemoved, pointer.segments.size)), documentSource
        )
    }
}

object UnknownSource : SourceLocation(0, 0, JsonPointer(emptyList())) {
    override fun toString(): String = "UNKNOWN"
}

interface IJsonValue {
    val location: SourceLocation
    private fun unexpectedType(expected: String): JsonTypingException = JsonTypingException(expected, jsonTypeAsString(), location)
    fun requireBoolean(): IJsonBoolean = throw unexpectedType("boolean")
    fun requireString(): IJsonString = throw unexpectedType("string")
    fun requireNumber(): IJsonNumber = throw unexpectedType("number")
    fun requireInt(): Int {
        val num = requireNumber();
        if (num.value is Int) return num.value.toInt() else throw unexpectedType("integer")
    }

    fun requireNull(): IJsonNull = throw unexpectedType("null")
    fun requireObject(): IJsonObject<*, *> = throw unexpectedType("object")
    fun requireArray(): IJsonArray<*> = throw unexpectedType("array")
    fun jsonTypeAsString(): String
}

interface IJsonString : IJsonValue {
    val value: String
    override fun jsonTypeAsString() = "string"
    override fun requireString(): IJsonString = this
}

interface IJsonBoolean : IJsonValue {
    val value: Boolean
    override fun jsonTypeAsString() = "boolean"
    override fun requireBoolean(): IJsonBoolean = this
}

interface IJsonNumber : IJsonValue {
    val value: Number
    override fun jsonTypeAsString() = "number"
    override fun requireNumber(): IJsonNumber = this
}

interface IJsonNull : IJsonValue {
    override fun jsonTypeAsString() = "null"
    override fun requireNull(): IJsonNull = this
}

interface IJsonArray<T : IJsonValue> : IJsonValue {
    val elements: List<T>
    override fun jsonTypeAsString() = "array"
    override fun requireArray(): IJsonArray<T> = this
    operator fun get(index: Int) = elements[index]
    fun length() = elements.size
}

interface IJsonObject<P : IJsonString, V : IJsonValue> : IJsonValue {
    val properties: Map<P, V>
    override fun jsonTypeAsString() = "object"
    override fun requireObject(): IJsonObject<P, V> = this
    operator fun get(key: String) = properties[JsonString(key) as P]
}

abstract class JsonValue(override val location: SourceLocation = UnknownSource) : IJsonValue

data class JsonNull(override val location: SourceLocation = UnknownSource) : JsonValue(location), IJsonNull

data class JsonBoolean(
        override val value: Boolean,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonBoolean

data class JsonNumber(
        override val value: Number,
        override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonNumber {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonNumber) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

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
