package com.github.erosb.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

class JsonParserTest {

    @Test
    fun `null parsing`() {
        val actual = JsonParser("null")()
        val expected = JsonNull(SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `leading and trailing whitespaces`() {
        val actual = JsonParser("\n\t \r null   \n")()
        val expected = JsonNull(SourceLocation(3, 2, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `CRLF counts as single line break`() {
        val actual = JsonParser("\r\n\r\n\t null \t  \r")()
        val expected = JsonNull(SourceLocation(3, 3, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `extraneous token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\t  null   null")()
        }
        assertEquals("Extraneous character found: n", exception.message)
    }

    @Test
    fun `null token mismatch`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nil")()
        }
        assertEquals(JsonParseException("Unexpected character found: i", TextLocation(1, 2)), exception)
    }

    @Test
    fun `EOF reached while reading token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nu")()
        }
        assertEquals(JsonParseException("Unexpected EOF", TextLocation(1, 3)), exception)
    }

    @Test
    fun `empty input`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("")()
        }
        assertEquals(JsonParseException("Unexpected EOF", TextLocation(1, 1)), exception)
    }

    @Test
    fun `string parsing`() {
        val actual = JsonParser("  \"string literal\"  ")()
        val expected = JsonString("string literal", SourceLocation(1, 3, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `escaped doublequotes in string`() {
        val actual = JsonParser("\"str\\\"ab\\\\c\\\"\"")()
        val expected = JsonString("str\"ab\\c\"", SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Nested
    class UnicodeEscapeSequenceTest {

        @Test
        fun `escaped unicode codepoint`() {
            val actual = JsonParser("\"\\u00E1\"")().requireString().value
            assertEquals("á", actual)
        }

        @Test
        fun `invalid unicode escape - invalid hex chars`() {
            val exception = assertThrows(JsonParseException::class.java) {
                JsonParser("\"p\\u022suffix\"")()
            }
            assertEquals(JsonParseException("invalid unicode sequence: 022s", TextLocation(1, 4)), exception)
        }

        @Test
        fun `invalid unicode escape - not enough hex chars`() {
            val exception = assertThrows(JsonParseException::class.java) {
                JsonParser("\"p\\u022")()
            }
            assertEquals(JsonParseException("Unexpected EOF", TextLocation(1, 8)), exception)
        }
    }

    @Test
    fun `unterminated string literal`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\r\n  \"")()
        }
        assertEquals(JsonParseException("Unexpected EOF", SourceLocation(2, 4, pointer())), exception)
    }

    @Test
    fun `array read`() {
        val actual = JsonParser(" [null, null\r\n]")()
        val expected = JsonArray(
            listOf(JsonNull(SourceLocation(1, 3, pointer("0"))), JsonNull(SourceLocation(1, 9, pointer("1")))),
            SourceLocation(1, 2, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `empty array`() {
        val actual = JsonParser("[  \n ]")()
        val expected = JsonArray(
            emptyList(),
            SourceLocation(1, 1, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `single-element array`() {
        val actual = JsonParser("[ null \n ]")()
        val expected = JsonArray(
            listOf(JsonNull(SourceLocation(1, 3, pointer("0")))),
            SourceLocation(1, 1, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `empty object`() {
        val actual = JsonParser("{}")()
        val expected = JsonObject(
            emptyMap(),
            SourceLocation(1, 1, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `single-property object`() {
        val actual = JsonParser("{\"key\":\"value\"}")()
        val expected = JsonObject(
            mapOf(Pair(JsonString("key", SourceLocation(1, 2, pointer("key"))), JsonString("value", SourceLocation(1, 8, pointer("key"))))),
            SourceLocation(1, 1, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `multi-property object`() {
        val actual = JsonParser(" {\"key\":\"value\", \"key2\" : null}\n")()
        val expected = JsonObject(
            mapOf(
                Pair(JsonString("key", SourceLocation(1, 3, pointer("key"))), JsonString("value", SourceLocation(1, 9, pointer("key")))),
                Pair(JsonString("key2", SourceLocation(1, 18, pointer("key2"))), JsonNull(SourceLocation(1, 27, pointer("key2"))))
            ),
            SourceLocation(1, 2, pointer())
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `boolean true`() {
        val actual = JsonParser("\ntrue")()
        val expected = JsonBoolean(true, SourceLocation(2, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `boolean false`() {
        val actual = JsonParser("\n  false")()
        val expected = JsonBoolean(false, SourceLocation(2, 3, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `positive int 123`() {
        val actual = JsonParser("123")()
        val expected = JsonNumber(123, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `negative int -123`() {
        val actual = JsonParser("-123")()
        val expected = JsonNumber(-123, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `Big Integer`() {
        val actual = JsonParser("9007199254740992")()
        assertEquals(BigInteger("9007199254740992"), actual.requireNumber().value)
    }

    @Test
    fun `Big Decimal`() {
        val str = "999" + Double.MAX_VALUE.toString()
        val actual = JsonParser(str)()
        assertEquals(BigDecimal(str), actual.requireNumber().value)
    }

    @Test
    fun `positive real 12_34`() {
        val actual = JsonParser("12.34")()
        val expected = JsonNumber(12.34, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `negative real -12_34`() {
        val actual = JsonParser("-12.34")()
        val expected = JsonNumber(-12.34, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `real with exponent 12_34e-22`() {
        val actual = JsonParser("12.34e-22")()
        val expected = JsonNumber(12.34e-22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `exponential without fractal`() {
        val actual = JsonParser("1e3")()
        val expected = JsonNumber(1e3, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `real with Exponent 12_34E+22`() {
        val actual = JsonParser("12.34E+22")()
        val expected = JsonNumber(12.34E+22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `negative real with Exponent -12_34E22`() {
        val actual = JsonParser("-12.34E22")()
        val expected = JsonNumber(-12.34E+22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @Test
    fun `complex object`() {
        val actual = JsonParser(
            """
            {
                "string": "árvíztűrő tükörfúrógép",
                "array": [null, true, 12.34],
                "object": { "": 0 }
            }
            """.trimIndent()
        )()

        val expected = JsonObject(
            mapOf(
                Pair(JsonString("array"), JsonArray(listOf(JsonNull(), JsonBoolean(true), JsonNumber(12.34)))),
                Pair(JsonString("string"), JsonString("árvíztűrő tükörfúrógép")),
                Pair(
                    JsonString("object"),
                    JsonObject(
                        mapOf(
                            Pair(JsonString(""), JsonNumber(0))
                        )
                    )
                )
            )
        )
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }
}
