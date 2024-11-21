package com.github.erosb.jsonsKema

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import kotlin.RuntimeException

private class SourceWalker(
    inputReader: Reader,
    private val documentSource: URI
) {

    private val reader = inputReader.let { if (it.markSupported()) it else BufferedReader(it) }

    constructor(input: InputStream, documentSource: URI) : this(
        BufferedReader(InputStreamReader(input)),
        documentSource
    )

    init {
        reader.markSupported()
    }

    private var lineNumber = 1
    private var position = 1

    fun curr(): Char {
        val currInt = currInt()
        if (currInt == -1) {
            throw JsonParseException("Unexpected EOF", location)
        }
        return currInt.toChar()
    }

    private fun currInt(): Int {
        reader.mark(1)
        val c = reader.read()
        reader.reset()
        return c
    }

    fun skipWhitespaces(): SourceWalker {
        while (true) {
            reader.mark(1)
            val c = reader.read()
            val char = c.toChar()
            if (c == -1 || !(char == ' ' || char == '\t' || char == '\n' || char == '\r')) {
                reader.reset()
                break
            }

            if (char == '\r' && currInt() == '\n'.toInt()) {
                reader.read()
            }

            if (char == '\n' || char == '\r') {
                position = 1
                ++lineNumber
            } else {
                ++position
            }
        }
        return this
    }

    fun reachedEOF(): Boolean {
        return currInt() == -1
    }

    fun consume(token: String): SourceWalker {
        token.chars().forEach { i ->
            val ch = curr()
            val toChar = i.toChar()
            if (toChar != ch) {
                throw JsonParseException("Unexpected character found: $ch", location)
            }
            reader.read()
            ++position
        }
        return this
    }

    fun forward() {
        reader.read()
        ++position
    }

    fun readUntil(terminator: Char): String {
        val buffer = StringBuilder()
        while (true) {
            val ch = curr()
            forward()
            if (ch == terminator) {
                break
            }
            buffer.append(ch)
        }
        return buffer.toString()
    }

    val location: TextLocation
        get() = TextLocation(lineNumber, position, documentSource)
}

private val DEFAULT_MAX_NESTING_DEPTH = 100_000

class TooDeeplyNestedValueException(loc: TextLocation, maxDepth: Int): RuntimeException("too deeply nested json value at line ${loc.lineNumber}, character ${loc.position}. Maximum nesting level in json structures is $maxDepth.")

class JsonParser private constructor(
    private val walker: SourceWalker,
    private val documentSource: URI,
    private val maxNestingDepth: Int
){
    private var currentNestingDepth = 0

    @JvmOverloads
    constructor(schemaJson: String, documentSource: URI = DEFAULT_BASE_URI, maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH)
    : this(SourceWalker(ByteArrayInputStream(schemaJson.toByteArray()), documentSource), documentSource, maxNestingDepth)

    @JvmOverloads
    constructor(schemaJson: Reader, documentSource: URI = DEFAULT_BASE_URI, maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH)
    : this(SourceWalker(schemaJson, documentSource), documentSource, maxNestingDepth)

    @JvmOverloads
    constructor(schemaInputStream: InputStream, documentSource: URI = DEFAULT_BASE_URI, maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH)
    : this(SourceWalker(schemaInputStream, documentSource), documentSource, maxNestingDepth)

    private val nestingPath: MutableList<String> = mutableListOf()

    fun parse(): JsonValue {
        val jsonValue = parseValue()
        if (!walker.reachedEOF()) {
            throw JsonParseException("Extraneous character found: ${walker.curr()}", walker.location)
        }
        return jsonValue
    }

    private fun parseValue(): JsonValue {
        walker.skipWhitespaces()
        currentNestingDepth++
        if (currentNestingDepth > maxNestingDepth) {
            throw TooDeeplyNestedValueException(sourceLocation(), maxNestingDepth)
        }
        val curr = walker.curr()
        val location = sourceLocation()
        var jsonValue: JsonValue? = null
        if (curr == 'n') {
            walker.consume("null")
            jsonValue = JsonNull(location)
        } else if (curr == '"') {
            jsonValue = parseString()
        } else if (curr == '[') {
            walker.forward()
            walker.skipWhitespaces()
            val elements = mutableListOf<JsonValue>()
            while (walker.curr() != ']') {
                var commaCharFound = false
                nestingPath.add(elements.size.toString().intern())
                val element = parseValue()
                elements.add(element)
                nestingPath.removeLast()
                if (walker.curr() == ',') {
                    commaCharFound = true
                    walker.forward()
                }
                walker.skipWhitespaces()
                val curr = walker.curr()
                if (!commaCharFound && walker.curr() != ']') {
                    throw JsonParseException("Unexpected character found: $curr. Expected ',', ']'", sourceLocation())
                }
            }
            walker.forward()
            jsonValue = JsonArray(elements.toList(), location)
        } else if (curr == '{') {
            val properties = mutableMapOf<JsonString, JsonValue>()
            walker.forward()
            walker.skipWhitespaces()
            while (walker.curr() != '}') {
                var commaCharFound = false
                val propName = parseString(true)
                walker.skipWhitespaces().consume(":").skipWhitespaces()
                val propValue = parseValue()
                nestingPath.removeLast()
                properties.put(propName, propValue)
                if (walker.curr() == ',') {
                    commaCharFound = true
                    walker.forward()
                }
                walker.skipWhitespaces()
                val curr = walker.curr()
                if (!commaCharFound && curr != '}') {
                    throw JsonParseException("Unexpected character found: $curr. Expected ',', '}'", sourceLocation())
                }
            }
            walker.forward()
            jsonValue = JsonObject(properties.toMap(), location)
        } else if (curr == 't') {
            walker.consume("true")
            jsonValue = JsonBoolean(true, location)
        } else if (curr == 'f') {
            walker.consume("false")
            jsonValue = JsonBoolean(false, location)
        } else if (curr == '-' || (curr in '0'..'9')) {
            jsonValue = parseNumber()
        }

        if (jsonValue == null) {
            throw JsonParseException("unexpected character $curr", sourceLocation())
        }
        --currentNestingDepth
        walker.skipWhitespaces()
        return jsonValue
    }

    private fun sourceLocation(): SourceLocation {
        val sourceLocation = SourceLocation(
            walker.location.lineNumber,
            walker.location.position,
            JsonPointer(nestingPath.toList()),
            documentSource,
        )
        return sourceLocation
    }

    private fun toNumber(str: String, location: SourceLocation): JsonNumber {
        try {
            return JsonNumber(str.toInt(), location)
        } catch (ex: NumberFormatException) {
            return JsonNumber(BigInteger(str), location)
        }
    }

    private fun toDouble(str: String, location: SourceLocation): JsonNumber {
        try {
            val value = str.toDouble()
            if (value.isInfinite()) {
                return JsonNumber(BigDecimal(str), location)
            }
            return JsonNumber(value, location)
        } catch (ex: NumberFormatException) {
            return JsonNumber(BigDecimal(str), location)
        }
    }

    private fun parseNumber(): JsonNumber {
        val location = sourceLocation()
        val buffer = StringBuilder()
        optParseSign(buffer)
        while (walker.curr() in '0'..'9' && !walker.reachedEOF()) {
            buffer.append(walker.curr())
            walker.forward()
            if (walker.reachedEOF()) {
                return toNumber(buffer.toString(), location)
            }
        }
        if (walker.curr() != '.' && walker.curr().lowercaseChar() != 'e') {
            return toNumber(buffer.toString(), location)
        }
        buffer.append(walker.curr())
        walker.forward()
        optParseSign(buffer)
        if (appendDigits(buffer)) return toDouble(buffer.toString(), location)
        if (!(walker.curr() == 'e' || walker.curr() == 'E')) {
            return toDouble(buffer.toString(), location)
        }
        buffer.append(walker.curr())
        walker.forward()
        optParseSign(buffer)
        if (appendDigits(buffer)) return toDouble(buffer.toString(), location)
        return toDouble(buffer.toString(), location)
    }

    private fun appendDigits(buffer: StringBuilder): Boolean {
        while (walker.curr() in '0'..'9') {
            buffer.append(walker.curr())
            walker.forward()
            if (walker.reachedEOF()) {
                return true
            }
        }
        return false
    }

    private fun optParseSign(buffer: StringBuilder) {
        val curr = walker.curr()
        if (curr == '-' || curr == '+') {
            buffer.append(curr)
            walker.forward()
        }
    }

    private fun parseString(putReadLiteralToNestingPath: Boolean = false): JsonString {
        var loc = sourceLocation()
        walker.consume("\"")
        var nextCharIsEscaped = false
        val sb = StringBuilder()
        var reachedClosingQuote = false
        while (!walker.reachedEOF()) {
            val ch = walker.curr()
            if (ch == '\\') {
                if (nextCharIsEscaped) {
                    nextCharIsEscaped = false
                } else {
                    nextCharIsEscaped = true
                    walker.forward()
                    continue
                }
            }
            if (ch == '"') {
                if (!nextCharIsEscaped) {
                    walker.forward()
                    reachedClosingQuote = true
                    break
                }
            }
            if (ch == 'u' && nextCharIsEscaped) {
                val location = walker.location
                val buf: Array<Char> = Array(4) { '0' }
                for (i in 0..3) {
                    walker.forward()
                    buf[i] = walker.curr()
                }
                val hexLiteral = buf.joinToString("")
                val trimmedHexLiteral = hexLiteral.trim { it == '0' }
                try {
                    val codePoint = if (trimmedHexLiteral.isEmpty()) 0 else Integer.parseInt(trimmedHexLiteral, 16)
                    sb.append(Character.valueOf(codePoint.toChar()))
                } catch (e: NumberFormatException) {
                    throw JsonParseException("invalid unicode sequence: $hexLiteral", location)
                }
            } else {
                sb.append(ch)
            }
            nextCharIsEscaped = false
            walker.forward()
        }
        if (!reachedClosingQuote) {
            throw JsonParseException("Unexpected EOF", sourceLocation())
        }
        val literal = sb.toString().intern() // walker.readUntil('"').intern()
        if (putReadLiteralToNestingPath) {
            nestingPath.add(literal)
            loc = SourceLocation(loc.lineNumber, loc.position, JsonPointer(nestingPath.toList()), documentSource)
            // no removal from nestingPath, call-site responsibility
        }
        return JsonString(literal, loc)
    }

    operator fun invoke(): JsonValue = parse()
}
