package com.github.erosb.jsonsKema

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import kotlin.RuntimeException

internal abstract class SourceWalker(
    private val documentSource: URI
) {

    protected val buf: CharArray = CharArray(1)

    protected var lineNumber = 1
    protected var position = 1


    fun skipWhitespaces() {
        while (hasAtLeastNRemainingChars(64)) {
            var ctr = 63
            while(ctr-- > 0) {
                val char = unsafeCurr()
                if (!(char == ' ' || char == '\t' || char == '\n' || char == '\r')) {
                    return
                }
                forward()

                if (char == '\r' && unsafeCurr() == '\n') {
                    forward()
                }

                if (char == '\n' || char == '\r') {
                    position = 1
                    ++lineNumber
                }
            }
        }
        while (true) {
            mark()
            val c = readCharInto()
            val char = buf[0]
            if (c == -1) {
                break
            }
            if (!(char == ' ' || char == '\t' || char == '\n' || char == '\r')) {
                reset()
                break
            }

            if (char == '\r' && currInt() == '\n'.code) {
                forward()
            }

            if (char == '\n' || char == '\r') {
                position = 1
                ++lineNumber
            }
        }
    }

    fun consume(token: String) {
        for (expected in token.toCharArray()) {
            val ch = curr()
            if (expected != ch) {
                throw JsonParseException("Unexpected character found: $ch", location)
            }
            forward()
        }
    }

    fun consume(expected: Char) {
        val ch = curr()
        if (expected != ch) {
            throw JsonParseException("Unexpected character found: $ch", location)
        }
        forward()
    }

    abstract fun readCharInto(): Int
    abstract fun forward()
    abstract fun curr(): Char
    abstract fun currInt(): Int
    abstract fun mark()
    abstract fun reset()
    abstract fun reachedEOF(): Boolean
    abstract fun hasAtLeastNRemainingChars(n: Int): Boolean
    abstract fun unsafeCurr(): Char

    val location: TextLocation
        get() = TextLocation(lineNumber, position, documentSource)
}

private class BufferReadingSourceWalker(
    inputReader: Reader,
    private val documentSource: URI
) : SourceWalker(documentSource) {

    constructor(input: InputStream, documentSource: URI) : this(
        BufferedReader(InputStreamReader(input)),
        documentSource
    )


    private val reader = inputReader.let { if (it.markSupported()) it else BufferedReader(it) }

    init {
        reader.markSupported()
    }

    override fun curr(): Char {
        val currInt = currInt()
        if (currInt == -1) {
            throw JsonParseException("Unexpected EOF", location)
        }
        return currInt.toChar()
    }

    override fun currInt(): Int {
        mark()
        val c = reader.read(buf)
        reset()
        return if (c == -1) -1 else buf[0].code
    }

    override fun readCharInto(): Int {
        return reader.read(buf)
    }

    override fun forward() {
        reader.skip(1)
        ++position
    }

    override fun reset() {
        reader.reset()
    }

    override fun mark() {
        reader.mark(1)
    }

    override fun reachedEOF(): Boolean {
        return currInt() == -1
    }

    override fun hasAtLeastNRemainingChars(n: Int): Boolean = false
    override fun unsafeCurr(): Char {
        mark()
        val c = reader.read()
        reset()
        if (c == -1) TODO()
        return c.toChar()
    }
}

private val DEFAULT_MAX_NESTING_DEPTH = 100_000

private val NULL_TOKEN = "null"//.toCharArray()
private val TRUE_TOKEN = "true"//.toCharArray()
private val FALSE_TOKEN = "false"//.toCharArray()

class TooDeeplyNestedValueException(loc: TextLocation, maxDepth: Int) :
    RuntimeException("too deeply nested json value at line ${loc.lineNumber}, character ${loc.position}. Maximum nesting level in json structures is $maxDepth.")

class JsonParser private constructor(
    private val walker: SourceWalker,
    private val documentSource: URI,
    private val maxNestingDepth: Int
) {
    private var currentNestingDepth = 0

    @JvmOverloads
    constructor(schemaJson: String, documentSource: URI = DEFAULT_BASE_URI, maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH)
            : this(StringReadingSourceWalker(schemaJson.toCharArray(), documentSource), documentSource, maxNestingDepth)

    @JvmOverloads
    constructor(schemaJson: Reader, documentSource: URI = DEFAULT_BASE_URI, maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH)
            : this(BufferReadingSourceWalker(schemaJson, documentSource), documentSource, maxNestingDepth)

    @JvmOverloads
    constructor(
        schemaInputStream: InputStream,
        documentSource: URI = DEFAULT_BASE_URI,
        maxNestingDepth: Int = DEFAULT_MAX_NESTING_DEPTH
    )
            : this(BufferReadingSourceWalker(schemaInputStream, documentSource), documentSource, maxNestingDepth)

    private var pathElem = PathElem(null, "")

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
            walker.consume(NULL_TOKEN)
            jsonValue = JsonNull(location)
        } else if (curr == '"') {
            jsonValue = parseString()
        } else if (curr == '[') {
            walker.forward()
            walker.skipWhitespaces()
            val elements = mutableListOf<JsonValue>()
            while (walker.curr() != ']') {
                var commaCharFound = false
                pathElem = PathElem(pathElem, elements.size.toString())
                val element = parseValue()
                elements.add(element)
                pathElem = pathElem.parent!!
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
                walker.skipWhitespaces()
                walker.consume(':')
                walker.skipWhitespaces()
                val propValue = parseValue()
                pathElem = pathElem.parent!!
                val previous = properties.put(propName, propValue)
                if (previous != null) {
                    throw DuplicateObjectPropertyException(
                        properties.keys.find { it.value == propName.value }!!,
                        propName
                    )
                }
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
            jsonValue = JsonObject(properties, location)
        } else if (curr == 't') {
            walker.consume(TRUE_TOKEN)
            jsonValue = JsonBoolean(true, location)
        } else if (curr == 'f') {
            walker.consume(FALSE_TOKEN)
            jsonValue = JsonBoolean(false, location)
        } else if (curr == '-' || (curr in '0'..'9')) {
            jsonValue = parseNumber()
        }

        if (jsonValue == null) {
            throw JsonParseException("unexpected character '$curr'", sourceLocation())
        }
        --currentNestingDepth
        walker.skipWhitespaces()
        return jsonValue
    }

    private fun sourceLocation(): SourceLocation {
        val textLocation = walker.location
        val sourceLocation = SourceLocation(
            textLocation.lineNumber,
            textLocation.position,
            JsonPointer(PathElemBackedList(pathElem)),
            documentSource,
        )
        return sourceLocation
    }

    private class PathElemBackedList(private val last: PathElem): AbstractList<String>() {

        var backingList: List<String>? = null

        private fun backingList(): List<String> {
            if (backingList != null) {
                return backingList!!
            }
            val lst = mutableListOf<String>()
            backingList = if (last.parent == null)  emptyList()
            else {
                var curr = last
                while (curr.parent != null) {
                    lst.add(curr.value)
                    curr = curr.parent
                }
                lst.reverse()
                lst
            }
            return backingList!!
        }



        override val size: Int
            get() = backingList().size

        override fun get(index: Int): String = backingList().get(index)

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
        sb.clear()
        optParseSign()
        while (walker.curr() in '0'..'9' && !walker.reachedEOF()) {
            sb.append(walker.curr())
            walker.forward()
            if (walker.reachedEOF()) {
                return toNumber(sb.toString(), location)
            }
        }
        if (walker.curr() != '.' && walker.curr().lowercaseChar() != 'e') {
            return toNumber(sb.toString(), location)
        }
        sb.append(walker.curr())
        walker.forward()
        optParseSign()
        if (appendDigits()) return toDouble(sb.toString(), location)
        if (!(walker.curr() == 'e' || walker.curr() == 'E')) {
            return toDouble(sb.toString(), location)
        }
        sb.append(walker.curr())
        walker.forward()
        optParseSign()
        if (appendDigits()) return toDouble(sb.toString(), location)
        return toDouble(sb.toString(), location)
    }

    private fun appendDigits(): Boolean {
        while (walker.curr() in '0'..'9') {
            sb.append(walker.curr())
            walker.forward()
            if (walker.reachedEOF()) {
                return true
            }
        }
        return false
    }

    private fun optParseSign() {
        val curr = walker.curr()
        if (curr == '-' || curr == '+') {
            sb.append(curr)
            walker.forward()
        }
    }

    private val sb = StringBuilder().also {it.ensureCapacity(100000)}

    private fun parseString(putReadLiteralToNestingPath: Boolean = false): JsonString {
        var loc = sourceLocation()
        walker.consume('"')
        var nextCharIsEscaped = false
        sb.clear()
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
        val literal = sb.toString()
        if (putReadLiteralToNestingPath) {
            pathElem = PathElem(pathElem, literal)
            loc = SourceLocation(loc.lineNumber, loc.position, JsonPointer(PathElemBackedList(pathElem)), documentSource)
            // no removal from nestingPath, call-site responsibility
        }
        return JsonString(literal, loc)
    }

    operator fun invoke(): JsonValue = parse()
}


private class PathElem(val parent: PathElem?, val value: String)
