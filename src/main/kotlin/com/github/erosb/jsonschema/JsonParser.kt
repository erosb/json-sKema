package com.github.erosb.jsonschema

import java.io.*

private class SourceWalker(
        private val input: InputStream,
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
        input.mark(1);
        val c = input.read()
        input.reset();
        return c;
    }

    fun skipWhitespaces() {
        while (true) {
            input.mark(Int.MAX_VALUE)
            val c = input.read();
            val char = c.toChar();
            if (c == -1 || !(char == ' ' || char == '\t' || char == '\n' || char == '\r')) {
                input.reset()
                break
            }
            
            if (char == '\r' && currInt() == '\n'.toInt()) {
                input.read();
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
            input.read()
            ++position
        }
    }

    val location: SourceLocation
        get() = SourceLocation(lineNumber, position, null);
}

class JsonParser(
        schemaJson: String,
        schemaInputStream: InputStream = ByteArrayInputStream(schemaJson.toByteArray())
) {

    private val walker: SourceWalker = SourceWalker(schemaInputStream);

    fun parse(): LocatedJsonValue {
        walker.skipWhitespaces()
        val curr = walker.curr()
        var jsonValue: LocatedJsonValue? = null;
        if (curr == 'n') {
            jsonValue = LocatedJsonNull(walker.location);
            walker.consume("null")
        }
        
        
        if (jsonValue == null) {
            TODO()
        }
        walker.skipWhitespaces();
        if (!walker.reachedEOF()) {
            throw JsonParseException("Extraneous character found: ${walker.curr()}", walker.location);
        }
        return jsonValue;
    }
    
    operator fun invoke(): LocatedJsonValue = parse()

}
