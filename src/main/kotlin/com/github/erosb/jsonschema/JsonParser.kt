package com.github.erosb.jsonschema

import java.io.*
import java.lang.StringBuilder

private class SourceWalker(
        input: InputStream,
        private val reader: Reader = BufferedReader(InputStreamReader(input))) {

    private var lineNumber = 1;
    private var position = 1;

    fun curr(): Char {
        val currInt = currInt()
        if (currInt == -1) {
            throw JsonParseException("Unexpected EOF", location)
        }
        return currInt.toChar();
    }

    private fun currInt(): Int {
        reader.mark(1);
        val c = reader.read()
        reader.reset();
        return c;
    }

    fun skipWhitespaces() {
        while (true) {
            reader.mark(1)
            val c = reader.read();
            val char = c.toChar();
            if (c == -1 || !(char == ' ' || char == '\t' || char == '\n' || char == '\r')) {
                reader.reset()
                break
            }

            if (char == '\r' && currInt() == '\n'.toInt()) {
                reader.read();
            }

            if (char == '\n' || char == '\r') {
                position = 1;
                ++lineNumber
            } else {
                ++position
            }
        }
    }

    fun reachedEOF(): Boolean {
        return currInt() == -1
    }

    fun consume(token: String) {
        token.chars().forEach { i ->
            val ch = curr();
            val toChar = i.toChar()
            if (toChar != ch) {
                throw JsonParseException("Unexpected character found: $ch", location)
            }
            reader.read()
            ++position
        }
    }

    fun forward() {
        reader.read();
        ++position;
    }

    fun readUntil(terminator: Char): String {
        val buffer = StringBuilder();
        while (true) {
            val ch = curr()
            forward();
            if (ch == terminator) {
                break;
            }
            buffer.append(ch)
        }
        return buffer.toString()
    }

    val location: SourceLocation
        get() = SourceLocation(lineNumber, position, null);
}

class JsonParser(
        schemaJson: String,
        schemaInputStream: InputStream = ByteArrayInputStream(schemaJson.toByteArray())
) {

    private val walker: SourceWalker = SourceWalker(schemaInputStream);

    fun parse(): JsonValue {
        val jsonValue = parseValue()
        if (!walker.reachedEOF()) {
            throw JsonParseException("Extraneous character found: ${walker.curr()}", walker.location);
        }
        return jsonValue;
    }

    private fun parseValue(): JsonValue {
        walker.skipWhitespaces()
        val curr = walker.curr()
        val location = walker.location;
        var jsonValue: JsonValue? = null;
        if (curr == 'n') {
            walker.consume("null")
            jsonValue = JsonNull(location);
        } else if (curr == '"') {
            jsonValue = parseString()
        } else if (curr == '[') {
            walker.forward();
            walker.skipWhitespaces();
            val elements = mutableListOf<JsonValue>()
            while (walker.curr() != ']') {
                elements.add(parseValue() as JsonValue)
                if (walker.curr() == ',') {
                    walker.forward();
                }
                walker.skipWhitespaces();
            }
            walker.forward()
            jsonValue = JsonArray(elements.toList()
                    , location)
        } else if (curr == '{') {
            val properties = mutableMapOf<JsonString, JsonValue>()
            walker.forward()
            walker.skipWhitespaces()
            while (walker.curr() != '}') {
                val propName = parseString() as JsonString
                walker.skipWhitespaces()
                walker.consume(":")
                walker.skipWhitespaces()
                val propValue = parseValue() as JsonValue
                properties.put(propName, propValue)
                if (walker.curr() == ',') {
                    walker.forward();
                }
                walker.skipWhitespaces()
            }
            walker.forward()
            jsonValue = JsonObject(properties.toMap(), location)
        } else if (curr == 't') {
            walker.consume("true");
            jsonValue = JsonBoolean(true, location)
        } else if (curr == 'f') {
            walker.consume("false");
            jsonValue = JsonBoolean(false, location)
        } else if (curr == '-' || (curr in '0'..'9')) {
            jsonValue = parseNumber()
        }


        if (jsonValue == null) {
            TODO()
        }
        walker.skipWhitespaces();
        return jsonValue
    }

    private fun parseNumber(): JsonNumber {
        val location = walker.location
        val buffer = StringBuilder()
        optParseSign(buffer)
        while(walker.curr() in '0'..'9' && !walker.reachedEOF()) {
            buffer.append(walker.curr())
            walker.forward()
            if (walker.reachedEOF()) {
                return JsonNumber(buffer.toString().toInt(), location)    
            }
        }
        if (walker.curr() != '.') {
            return JsonNumber(buffer.toString().toInt(), location)
        }
        buffer.append(".");
        walker.forward()
        if (appendDigits(buffer)) return JsonNumber(buffer.toString().toDouble(), location)
        if (!(walker.curr() == 'e' || walker.curr() == 'E')) {
            return JsonNumber(buffer.toString().toDouble(), location)    
        }
        buffer.append(walker.curr())
        walker.forward()
        optParseSign(buffer);
        if (appendDigits(buffer)) return JsonNumber(buffer.toString().toDouble(), location)
        return JsonNumber(buffer.toString().toDouble(), location)
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

    private fun parseString(): JsonString {
        val loc = walker.location;
        walker.consume("\"")
        val literal = walker.readUntil('"');
        return JsonString(literal, loc)
    }

    operator fun invoke(): JsonValue = parse()

}
