package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JsonParserTest {

    @Test fun `null parsing`() {
        val actual = JsonParser("null")();
        val expected = LocatedJsonNull(SourceLocation(1, 1));
        assertEquals(expected, actual);
    }
    
    @Test fun `leading and trailing whitespaces`() {
        val actual = JsonParser("\n\t \r null   \n")();
        val expected = LocatedJsonNull(SourceLocation(3, 2));
        assertEquals(expected, actual);
    }
    
    @Test
    fun `CRLF counts as single line break`() {
        val actual = JsonParser("\r\n\r\n\t null \t  \r")();
        val expected = LocatedJsonNull(SourceLocation(3, 3));
        assertEquals(expected, actual);
    }
    
    @Test fun `extraneous token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\t  null   null")()    
        };
        assertEquals("Extraneous character found: n", exception.message)
    }
    
    @Test fun `null token mismatch`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nil")()
        };
        assertEquals(JsonParseException("Unexpected character found: i", SourceLocation(1, 2)), exception)
    }
    
    @Test fun `EOF reached while reading token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nu")()
        };
        assertEquals(JsonParseException("Unexpected EOF", SourceLocation(1, 3)), exception)
    }
    
    @Test fun `empty input`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("")()
        };
        assertEquals(JsonParseException("Unexpected EOF", SourceLocation(1, 1)), exception)
    }
}
