package com.github.erosb.jsonsKema

import org.yaml.snakeyaml.Yaml
import java.io.StringReader
import java.math.BigDecimal
import java.net.URI
import java.util.stream.Collectors.joining

open class JsonParseException(override val message: String, val location: TextLocation) : RuntimeException()

class DuplicateObjectPropertyException(
    firstOccurrence: JsonString,
    secondOccurrence: JsonString
): JsonParseException("property \"${firstOccurrence.value}\" found at multiple locations in the same object, ${firstOccurrence.location} and ${secondOccurrence.location}", secondOccurrence.location)

data class JsonTypingException(
    val expectedType: String,
    val actualType: String,
    val location: SourceLocation
) : RuntimeException("${location.pointer}: expected $expectedType, found $actualType (line ${location.lineNumber}, position ${location.position})") {
    override fun toString() = message ?: ""
}

data class JsonPointer(val segments: List<String>) {

    constructor(vararg segments: String): this(listOf(*segments))

    override fun toString() = "#" + (if (segments.isEmpty()) "" else "/") + segments.joinToString("/") {
        it.replace("~", "~0").replace("/", "~1")
    }

    operator fun plus(additionalSegment: String): JsonPointer = JsonPointer(segments + additionalSegment)

    operator fun plus(additionalSegment: Keyword): JsonPointer = plus(additionalSegment.value)
}

fun pointer(vararg segments: String) = JsonPointer(segments.toList())

open class TextLocation(val lineNumber: Int, val position: Int, val documentSource: URI) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is TextLocation) return false

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
        return "line $lineNumber, character $position"
    }
}

open class SourceLocation(
    lineNumber: Int,
    position: Int,
    val pointer: JsonPointer,
    documentSource: URI = DEFAULT_BASE_URI
) : TextLocation(lineNumber, position, documentSource) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false
        if (other !is SourceLocation) return false

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

    fun getLocation(): String {
        val sb = StringBuilder()
        if (documentSource !== null) {
            sb.append(documentSource).append(": ")
        }
        sb.append("Line $lineNumber, character $position")
        return sb.toString()
    }

    override fun toString(): String {
        return "$documentSource$pointer" + (
                if (lineNumber > -1 && position> -1) " (Line $lineNumber, character $position)"
                else "")
    }

    internal fun trimPointerSegments(leadingSegmentsToBeRemoved: Int): SourceLocation {
        if (leadingSegmentsToBeRemoved > pointer.segments.size) {
            throw IllegalArgumentException("can not remove $leadingSegmentsToBeRemoved segment from pointer $pointer")
        }
        return SourceLocation(
            lineNumber,
            position,
            JsonPointer(pointer.segments.subList(leadingSegmentsToBeRemoved, pointer.segments.size)),
            documentSource
        )
    }

    fun withPointer(pointer: JsonPointer): SourceLocation = SourceLocation(lineNumber, position, pointer, documentSource)

    internal operator fun plus(additionalSegment: String): SourceLocation =
        SourceLocation(lineNumber, position, pointer + additionalSegment, documentSource)

    internal operator fun plus(additionalSegment: Keyword): SourceLocation =
        SourceLocation(lineNumber, position, pointer + additionalSegment, documentSource)
}

object UnknownSource : SourceLocation(-1, -1, JsonPointer(emptyList()), URI("UNKNOWN")) {
    override fun toString(): String = "UNKNOWN"
}

interface IJsonValue {
    val location: SourceLocation
    private fun unexpectedType(expected: String): JsonTypingException =
        JsonTypingException(expected, jsonTypeAsString(), location)

    fun requireBoolean(): IJsonBoolean = throw unexpectedType("boolean")
    fun requireString(): IJsonString = throw unexpectedType("string")
    fun requireNumber(): IJsonNumber = throw unexpectedType("number")
    fun requireInt(): Int = requireNumber().value.toInt()

    fun requireNull(): IJsonNull = throw unexpectedType("null")
    fun requireObject(): IJsonObject<*, *> = throw unexpectedType("object")
    fun requireArray(): IJsonArray<*> = throw unexpectedType("array")

    fun <P> maybeString(fn: (IJsonString) -> P?): P? = null
    fun <P> maybeNumber(fn: (IJsonNumber) -> P?): P? = null
    fun <P> maybeArray(fn: (IJsonArray<*>) -> P?): P? = null
    fun <P> maybeObject(fn: (IJsonObject<*, *>) -> P?): P? = null

    fun <P> accept(visitor: JsonVisitor<P>): P?

    fun jsonTypeAsString(): String
}

interface IJsonString : IJsonValue {
    val value: String
    override fun jsonTypeAsString() = "string"
    override fun requireString(): IJsonString = this
    override fun <P> maybeString(fn: (IJsonString) -> P?): P? = fn(this)
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitString(this)
}

interface IJsonBoolean : IJsonValue {
    val value: Boolean
    override fun jsonTypeAsString() = "boolean"
    override fun requireBoolean(): IJsonBoolean = this
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitBoolean(this)
}

interface IJsonNumber : IJsonValue {
    val value: Number
    override fun jsonTypeAsString() = "number"
    override fun requireNumber(): IJsonNumber = this
    override fun <P> maybeNumber(fn: (IJsonNumber) -> P?): P? = fn(this)
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitNumber(this)
}

interface IJsonNull : IJsonValue {
    override fun jsonTypeAsString() = "null"
    override fun requireNull(): IJsonNull = this
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitNull(this)
}

interface IJsonArray<T : IJsonValue> : IJsonValue {
    val elements: List<T>
    override fun jsonTypeAsString() = "array"
    override fun requireArray(): IJsonArray<T> = this
    override fun <P> maybeArray(fn: (IJsonArray<*>) -> P?): P? = fn(this)
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitArray(this)
    operator fun get(index: Int) = elements[index]
    fun length() = elements.size
    fun markUnevaluated(idx: Int) {}
    fun markEvaluated(idx: Int): IJsonValue
    fun markAllEvaluated()
}

interface IJsonObject<P : IJsonString, V : IJsonValue> : IJsonValue {
    val properties: Map<P, V>
    override fun jsonTypeAsString() = "object"
    override fun requireObject(): IJsonObject<P, V> = this
    override fun <P> maybeObject(fn: (IJsonObject<*, *>) -> P?): P? = fn(this)
    override fun <P> accept(visitor: JsonVisitor<P>): P? = visitor.visitObject(this)

    operator fun get(key: String) = properties[JsonString(key) as P]
    fun markUnevaluated(propName: String)
    fun markEvaluated(propName: String)
}

typealias IJsonObj = IJsonObject<*, *>

interface JsonVisitor<P> {
    fun identity(): P? = null
    fun accumulate(previous: P?, current: P?): P? = current ?: previous
    fun visitString(str: IJsonString): P?
    fun visitBoolean(bool: IJsonBoolean): P?
    fun visitNumber(num: IJsonNumber): P?
    fun visitNull(nil: IJsonNull): P?
    fun visitArray(arr: IJsonArray<*>): P?
    fun visitObject(obj: IJsonObject<*, *>): P?
}

abstract class JsonValue(override val location: SourceLocation = UnknownSource) : IJsonValue {

    companion object {

        private val yamlSupport = runCatching {
            Class.forName("org.yaml.snakeyaml.Yaml")
        }.isSuccess

        /**
         * Parses the source string into a JsonValue. The source can be either a JSON string or a
         * YAML document.
         */
        @JvmOverloads
        @JvmStatic
        fun parse(source: String, documentSource: URI = DEFAULT_BASE_URI): JsonValue {
            try {
                return JsonParser(source, documentSource)()
            } catch (ex: JsonParseException) {
                if (yamlSupport) {
                    try {
                        return loadFromYaml(Yaml().compose(StringReader(source)), documentSource)
                    } catch (e: RuntimeException) {
                        if (ex.location.lineNumber == 1 && ex.location.position == 1) {
                            val yamlDocEx = YamlParseException(e)
                            yamlDocEx.addSuppressed(ex)
                            throw yamlDocEx
                        }
                    }
                }
                throw ex
            }
        }
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (!other.javaClass.isAssignableFrom(javaClass)) return false
        return unwrap() == (other as JsonValue).unwrap()
    }

    final override fun hashCode(): Int {
        return unwrap()?.hashCode() ?: 0
    }

    internal abstract fun unwrap(): Any?

    final override fun toString(): String = accept(JsonPrintingVisitor())!!
}

data class JsonNull @JvmOverloads constructor(override val location: SourceLocation = UnknownSource) : IJsonNull, JsonValue(location) {
    override fun unwrap(): Any? = null
    override fun equals(other: Any?) = super.equals(other)
}

data class JsonBoolean @JvmOverloads constructor(
    override val value: Boolean,
    override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonBoolean {
    override fun unwrap(): Any = value
    override fun equals(other: Any?) = super.equals(other)
}

data class JsonNumber @JvmOverloads constructor(
    override val value: Number,
    override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonNumber {
    override fun unwrap() = value

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is IJsonNumber) return false
        return BigDecimal(value.toString()).compareTo(BigDecimal(other.value.toString())) == 0
    }
}

data class JsonString @JvmOverloads constructor(
    override val value: String,
    override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonString {

    override fun equals(other: Any?) = super.equals(other)

    override fun unwrap() = value
}

data class JsonArray @JvmOverloads constructor(
    override val elements: List<JsonValue>,
    override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonArray<JsonValue> {
    override fun unwrap() = elements
    override fun markEvaluated(idx: Int): IJsonValue = get(idx)
    override fun markAllEvaluated() {
    }

    override fun equals(other: Any?) = super.equals(other)
}

data class JsonObject @JvmOverloads constructor(
    override val properties: Map<JsonString, JsonValue>,
    override val location: SourceLocation = UnknownSource
) : JsonValue(location), IJsonObject<JsonString, JsonValue> {
    override fun unwrap() = properties
    override fun markUnevaluated(propName: String) {}

    override fun markEvaluated(propName: String) {}

    override fun equals(other: Any?) = super.equals(other)
}
