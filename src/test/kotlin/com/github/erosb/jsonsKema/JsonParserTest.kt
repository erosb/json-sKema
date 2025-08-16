package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.BigInteger

class JsonParserTest {

    companion object {
        @JvmStatic
        fun parsers(): List<Arguments> {
            return listOf(
                Arguments.of({ str: String -> JsonParser(str) }),
                Arguments.of({ str: String -> JsonParser(ByteArrayInputStream(str.toByteArray())) })
            )
        }
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `null parsing`(parser: (String) -> JsonParser) {
        val actual = parser("null")()
        val expected = JsonNull(SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `leading and trailing whitespaces`() {
        val actual = JsonParser("\n\t \r null   \n")()
        val expected = JsonNull(SourceLocation(3, 2, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `CRLF counts as single line break`() {
        val actual = JsonParser("\r\n\r\n\t null \t  \r")()
        val expected = JsonNull(SourceLocation(3, 3, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `extraneous token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\t  null   null")()
        }
        assertEquals("Extraneous character found: n", exception.message)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `create from Reader`() {
        val parser = JsonParser(
            BufferedReader(
                InputStreamReader(ByteArrayInputStream("true".toByteArray()))
            )
        )
        assertEquals(JsonBoolean(true, SourceLocation(1, 1, JsonPointer())), parser.parse())
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `create from non-buffered`() {
        val parser = JsonParser(
            InputStreamReader(ByteArrayInputStream("  true".toByteArray()))
        )
        assertEquals(JsonBoolean(true, SourceLocation(1, 3, JsonPointer())), parser.parse())
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `too deep nesting`() {
        val actual = assertThrows(TooDeeplyNestedValueException::class.java) {
            JsonParser(
                """
            [
                {
                    "a": [
                        {
                            "b": [
                                {}
                            ]
                        }
                    ]
                }
            ]
        """.trimIndent(), DEFAULT_BASE_URI, 5
            )()
        }
        assertThat(actual.message).isEqualTo("too deeply nested json value at line 6, character 21. Maximum nesting level in json structures is 5.")
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `not too deep nesting`() {
        JsonParser(
            """
        [
            {
                "a": [
                ]
            },
            [],
            [],
            [],
            []
        ]
    """.trimIndent(), DEFAULT_BASE_URI, 5
        )()
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `null token mismatch`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nil")()
        }
        assertEquals("Unexpected character found: i", exception.message)
        assertEquals(TextLocation(1, 2, DEFAULT_BASE_URI), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `EOF reached while reading token`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("nu")()
        }
        assertEquals("Unexpected EOF", exception.message)
        assertEquals(TextLocation(1, 3, DEFAULT_BASE_URI), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `empty input`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("")()
        }
        assertEquals("Unexpected EOF", exception.message)
        assertEquals(TextLocation(1, 1, DEFAULT_BASE_URI), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `missing comma character in object`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("{\"key1\":\"value1\", \"key2\":\"value2\" \"key3\":\"value3\"}\n")()
        }
        assertEquals("Unexpected character found: \". Expected ',', '}'", exception.message)
        assertEquals(SourceLocation(1, 35, pointer()), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `missing comma character in array`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("[1, 2 3]")()
        }
        assertEquals("Unexpected character found: 3. Expected ',', ']'", exception.message)
        assertEquals(SourceLocation(1, 7, pointer()), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `string parsing`() {
        val actual = JsonParser("  \"string literal\"  ")()
        val expected = JsonString("string literal", SourceLocation(1, 3, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `escaped doublequotes in string`() {
        val actual = JsonParser("\"str\\\"ab\\\\c\\\"\"")()
        val expected = JsonString("str\"ab\\c\"", SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

//    @Nested
//    inner class UnicodeEscapeSequenceTest {
//
//        companion object {
//            @JvmStatic
//            fun parsers() = JsonParserTest.parsers()
//        }

        @ParameterizedTest
        @MethodSource("parsers")
        fun `escaped unicode codepoint`() {
            val actual = JsonParser("\"\\u00E1\"")().requireString().value
            assertEquals("á", actual)
        }

        @ParameterizedTest
        @MethodSource("parsers")
        fun `invalid unicode escape - invalid hex chars`() {
            val exception = assertThrows(JsonParseException::class.java) {
                JsonParser("\"p\\u022suffix\"")()
            }
            assertEquals("invalid unicode sequence: 022s", exception.message)
            assertEquals(TextLocation(1, 4, DEFAULT_BASE_URI), exception.location)
        }

        @ParameterizedTest
        @MethodSource("parsers")
        fun `invalid unicode escape - not enough hex chars`() {
            val exception = assertThrows(JsonParseException::class.java) {
                JsonParser("\"p\\u022")()
            }
            assertEquals("Unexpected EOF", exception.message)
            assertEquals(TextLocation(1, 8, DEFAULT_BASE_URI), exception.location)
        }

        @ParameterizedTest
        @MethodSource("parsers")
        fun `supplementary codepoint`() {
            val str = JsonParser("\"\\uD83D\\uDCA9\"")().requireString().value
            assertEquals("💩", str)
        }
//    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `unterminated string literal`() {
        val exception = assertThrows(JsonParseException::class.java) {
            JsonParser("\r\n  \"")()
        }
        assertEquals("Unexpected EOF", exception.message)
        assertEquals(SourceLocation(2, 4, pointer()), exception.location)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `array read`() {
        val actual = JsonParser(" [null, null\r\n]")()
        val expected = JsonArray(
            listOf(JsonNull(SourceLocation(1, 3, pointer("0"))), JsonNull(SourceLocation(1, 9, pointer("1")))),
            SourceLocation(1, 2, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `empty array`() {
        val actual = JsonParser("[  \n ]")()
        val expected = JsonArray(
            emptyList(),
            SourceLocation(1, 1, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `single-element array`() {
        val actual = JsonParser("[ null \n ]")()
        val expected = JsonArray(
            listOf(JsonNull(SourceLocation(1, 3, pointer("0")))),
            SourceLocation(1, 1, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `empty object`() {
        val actual = JsonParser("{}")()
        val expected = JsonObject(
            emptyMap(),
            SourceLocation(1, 1, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `single-property object`() {
        val actual = JsonParser("{\"key\":\"value\"}")()
        val expected = JsonObject(
            mapOf(
                Pair(
                    JsonString("key", SourceLocation(1, 2, pointer("key"))),
                    JsonString("value", SourceLocation(1, 8, pointer("key")))
                )
            ),
            SourceLocation(1, 1, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `multi-property object`() {
        val actual = JsonParser(" {\"key\":\"value\", \"key2\" : null}\n")()
        val expected = JsonObject(
            mapOf(
                Pair(
                    JsonString("key", SourceLocation(1, 3, pointer("key"))),
                    JsonString("value", SourceLocation(1, 9, pointer("key")))
                ),
                Pair(
                    JsonString("key2", SourceLocation(1, 18, pointer("key2"))),
                    JsonNull(SourceLocation(1, 27, pointer("key2")))
                ),
            ),
            SourceLocation(1, 2, pointer()),
        )
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `boolean true`() {
        val actual = JsonParser("\ntrue")()
        val expected = JsonBoolean(true, SourceLocation(2, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `boolean false`() {
        val actual = JsonParser("\n  false")()
        val expected = JsonBoolean(false, SourceLocation(2, 3, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `positive int 123`() {
        val actual = JsonParser("123")()
        val expected = JsonNumber(123, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `negative int -123`() {
        val actual = JsonParser("-123")()
        val expected = JsonNumber(-123, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `Big Integer`() {
        val actual = JsonParser("9007199254740992")()
        assertEquals(BigInteger("9007199254740992"), actual.requireNumber().value)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `Big Decimal`() {
        val str = "999" + Double.MAX_VALUE.toString()
        val actual = JsonParser(str)()
        assertEquals(BigDecimal(str), actual.requireNumber().value)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `positive real 12_34`() {
        val actual = JsonParser("12.34")()
        val expected = JsonNumber(12.34, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `negative real -12_34`() {
        val actual = JsonParser("-12.34")()
        val expected = JsonNumber(-12.34, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `real with exponent 12_34e-22`() {
        val actual = JsonParser("12.34e-22")()
        val expected = JsonNumber(12.34e-22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `exponential without fractal`() {
        val actual = JsonParser("1e3")()
        val expected = JsonNumber(1e3, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `negative exponential without fractal`() {
        val actual = JsonParser("1e-8")()
        val expected = JsonNumber(1e-8, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `real with Exponent 12_34E+22`() {
        val actual = JsonParser("12.34E+22")()
        val expected = JsonNumber(12.34E+22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `negative real with Exponent -12_34E22`() {
        val actual = JsonParser("-12.34E22")()
        val expected = JsonNumber(-12.34E+22, SourceLocation(1, 1, pointer()))
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `complex object`() {
        val actual = JsonParser(
            """
            {
                "string": "árvíztűrő tükörfúrógép",
                "array": [null, true, 12.34],
                "object": { "": 0 }
            }
            """.trimIndent(),
        )()

        val expected = JsonObject(
            mapOf(
                Pair(JsonString("array"), JsonArray(listOf(JsonNull(), JsonBoolean(true), JsonNumber(12.34)))),
                Pair(JsonString("string"), JsonString("árvíztűrő tükörfúrógép")),
                Pair(
                    JsonString("object"),
                    JsonObject(
                        mapOf(
                            Pair(JsonString(""), JsonNumber(0)),
                        ),
                    ),
                ),
            ),
        )
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `duplicate key causes exception`() {
        assertThrows(JsonParseException::class.java) {
            JsonParser(
                """
            {
                "a": 2,
                "b": 2,
                "a": 3
            }
        """.trimIndent()
            )()
        }
    }

    @ParameterizedTest
    @MethodSource("parsers")
    fun `duplicate key check works only on same nesting level`() {
        JsonParser(
            """
            {
                "a": {
                    "a": 2,
                    "b": [ "a" ]
                },
                "b": 2,
                "c": [
                    "a",
                    {"a": false},
                    "a",
                    [ "a" ]
                ]
            }
        """.trimIndent()
        )()
    }
}
