package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI

class SchemaLoadingFailureTest {

    @Test
    fun `intra-doc failure on object prop`() {
        val subject = SchemaLoader(
            JsonParser(
                """
            {
                "$ref": "#/$defs/missing",
                "$defs": {
                    "Present": false
                }
            }
        """.trimIndent(), URI("my-uri")).parse())

        val expected = RefResolutionException(
            ref = ReferenceSchema(null, "my-uri#/$defs/missing", SourceLocation(2, 5, JsonPointer("$ref"), URI("my-uri"))),
            missingProperty = "missing",
            resolutionFailureLocation = SourceLocation(3, 14, JsonPointer("$defs"), URI("my-uri"))
        )

        assertThatThrownBy { subject.load() }
            .isEqualTo(expected)
            .hasMessage("$ref resolution failure: could not evaluate pointer \"my-uri#/$defs/missing\", property \"missing\" not found at my-uri: Line 3, character 14")
    }

    @Test
    fun `array index missing`() {
        val subject = SchemaLoader(
            JsonParser(
                """
            {
                "type": "object",
                "properties": {
                    "prop": {
                        "$ref": "#/definitions/0/missing"                    
                    }
                },
                "definitions": []
            }
        """.trimIndent()).parse())

        val expected = RefResolutionException(
            ref = ReferenceSchema(null, "mem://input#/definitions/0/missing", SourceLocation(5, 13, JsonPointer(listOf("properties", "prop", "$ref")))),
            missingProperty = "0",
            resolutionFailureLocation = SourceLocation(8, 20, JsonPointer(listOf("definitions")))
        )
        assertThatThrownBy { subject.load() }
            .isEqualTo(expected)
    }

    @Test
    fun `lookup failure in remote schema`() {
        val subject = SchemaLoader(schemaJson = JsonParser("""
            {
                "additionalProperties": {
                    "$ref": "urn:asdasd#/$defs/Hello"
                }
            }            
        """.trimIndent())(), config = createDefaultConfig(mapOf(
            URI("urn:asdasd") to """
                {
                    "$defs": {
                        "World": true
                    }
                }
            """.trimIndent()
        ))
        )

        val expected = RefResolutionException(
            ref = ReferenceSchema(
                null,
                "urn:asdasd#/$defs/Hello",
                SourceLocation(3, 9, JsonPointer(listOf("additionalProperties", "$ref")))
            ),
            missingProperty = "Hello",
            resolutionFailureLocation = SourceLocation(2, 14, JsonPointer(listOf("$defs")), URI("urn:asdasd"))
        )

        assertThatThrownBy { subject.load() }
            .usingRecursiveComparison()
            .isEqualTo(expected)
    }

    @Test
    fun `remote failure`() {
        val subject = SchemaLoader(
            JsonParser(
                """
            {
               "$ref": "classpath://.non-existent.file"
            }
        """.trimIndent()).parse())

        assertThatThrownBy { subject.load() }
            .isInstanceOf(SchemaLoadingException::class.java)
    }

    @Test
    fun `multiple failures`() {
        val subject = SchemaLoader(
            schemaJson = JsonParser("""
                {
                    "title": null,
                    "description": 2,
                    "properties": {
                        "wrongType": {
                            "type": "float",
                            "minimum": "maybe 2 or so"
                        },
                        "remoteNotFound": {
                            "$ref": "classpath://not-found.file"
                        },
                        "remotePointerFailure": {
                            "$ref": "http://example.org/schema#/$defs/X"
                        },
                        "remoteParsingFailure": {
                            "$ref": "classpath://xml"
                        }
                    }
                }
            """.trimIndent())(),
            config = createDefaultConfig(mapOf(
                URI("http://example.org/schema") to """
                    {
                        "$defs": {}
                    }
                """.trimIndent(),
                URI("classpath://xml") to """
                    x:
                      - [[[[
                      [[[[]y

                """.trimIndent()
            ))
        )

        try {
            subject.load()
            fail("did not throw exception")
        } catch (ex: AggregateSchemaLoadingException) {
            ex.causes.forEach { println(it.javaClass.simpleName) }
        }
    }

    @Test
    fun `IOException in get() is mapped to SchemaLoadingException`() {
        val loader = SchemaLoader(JsonValue.parse("""
            {"$ref": "http://example.org"}
        """, DEFAULT_BASE_URI), SchemaLoaderConfig(
            schemaClient = { throw UncheckedIOException("msg", IOException()) }
        )
        )

        assertThatThrownBy {
            loader()
        }.isInstanceOf(SchemaLoadingException::class.java)
    }
}
