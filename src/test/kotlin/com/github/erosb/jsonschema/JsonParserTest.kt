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
    
    @Test fun `string parsing`() {
        val actual = JsonParser("  \"string literal\"  ")();
        val expected = LocatedJsonString("string literal", SourceLocation(1, 3));
        assertEquals(expected, actual)
    }
    
    @Test fun `unterminated string literal`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\r\n  \"")()
        };
        assertEquals(JsonParseException("Unexpected EOF", SourceLocation(2, 4)), exception)
    }
    
    @Test fun `array read`() {
        val actual = JsonParser(" [null, null\r\n]")();
        val expected = LocatedJsonArray(
                listOf(LocatedJsonNull(SourceLocation(1, 3)), LocatedJsonNull(SourceLocation(1, 9))),
                SourceLocation(1, 2)
        );
        assertEquals(expected, actual)
    }

    @Test fun `empty array`() {
        val actual = JsonParser("[  \n ]")();
        val expected = LocatedJsonArray(
                emptyList(),
                SourceLocation(1, 1)
        );
        assertEquals(expected, actual)
    }

    @Test fun `single-element array`() {
        val actual = JsonParser("[ null \n ]")();
        val expected = LocatedJsonArray(
                listOf(LocatedJsonNull(SourceLocation(1, 3))),
                SourceLocation(1, 1)
        );
        assertEquals(expected, actual)
    }
    
    @Test fun `empty object`() {
        val actual = JsonParser("{}")()
        val expected = LocatedJsonObject(
                emptyMap(),
                SourceLocation(1, 1)
        )
        assertEquals(expected, actual);
    }
    
    @Test fun `single-property object`() {
        val actual = JsonParser("{\"key\":\"value\"}")()
        val expected = LocatedJsonObject(
                mapOf(Pair(LocatedJsonString("key", SourceLocation(1, 2)), LocatedJsonString("value", SourceLocation(1, 8)))),
                SourceLocation(1, 1)
        )
        assertEquals(expected, actual);
    }

    @Test fun `multi-property object`() {
        val actual = JsonParser(" {\"key\":\"value\", \"key2\" : null}\n")()
        val expected = LocatedJsonObject(
                mapOf(
                        Pair(LocatedJsonString("key", SourceLocation(1, 3)), LocatedJsonString("value", SourceLocation(1, 9))),
                        Pair(LocatedJsonString("key2", SourceLocation(1, 18)), LocatedJsonNull(SourceLocation(1, 27)))
                ),
                SourceLocation(1, 2)
        )
        assertEquals(expected, actual);
    }
    
    @Test fun `boolean true`() {
        val actual = JsonParser("\ntrue")();
        val expected = LocatedJsonBoolean(true, SourceLocation(2, 1));
        assertEquals(expected, actual);
    }

    @Test fun `boolean false`() {
        val actual = JsonParser("\n  false")();
        val expected = LocatedJsonBoolean(false, SourceLocation(2, 3));
        assertEquals(expected, actual);
    }
    
    @Test fun `positive int 123`() {
        val actual = JsonParser("123")()
        val expected = LocatedJsonNumber(123, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }
    
    @Test fun `negative int -123`() {
        val actual = JsonParser("-123")()
        val expected = LocatedJsonNumber(-123, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }
    
    @Test fun `positive real 12_34`() {
        val actual = JsonParser("12.34")()
        val expected = LocatedJsonNumber(12.34, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }

    @Test fun `negative real -12_34`() {
        val actual = JsonParser("-12.34")()
        val expected = LocatedJsonNumber(-12.34, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }

    @Test fun `real with exponent 12_34e-22`() {
        val actual = JsonParser("12.34e-22")()
        val expected = LocatedJsonNumber(12.34e-22, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }

    @Test fun `real with Exponent 12_34E+22`() {
        val actual = JsonParser("12.34E+22")()
        val expected = LocatedJsonNumber(12.34E+22, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }

    @Test fun `negative real with Exponent -12_34E22`() {
        val actual = JsonParser("-12.34E22")()
        val expected = LocatedJsonNumber(-12.34E+22, SourceLocation(1, 1))
        assertEquals(expected, actual)
    }
}
