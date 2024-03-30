package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.fail
import org.assertj.core.api.Assertions.from
import org.junit.jupiter.api.Test
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
        """.trimIndent()).parse())

        val expected = RefResolutionException(
            ref = ReferenceSchema(null, "mem://input#/$defs/missing", SourceLocation(2, 5, JsonPointer(listOf("$ref")))),
            missingProperty = "missing",
            resolutionFailureLocation = SourceLocation(3, 14, JsonPointer(listOf("$defs")))
        )

        Assertions.assertThatThrownBy { subject.load() }
            .isEqualTo(expected)
            .hasMessage("$ref resolution failure: could not evaluate pointer \"mem://input#/$defs/missing\", property \"missing\" not found at Line 3, character 14")
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
        );
        Assertions.assertThatThrownBy { subject.load() }
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

        Assertions.assertThatThrownBy { subject.load() }
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

        Assertions.assertThatThrownBy { subject.load() }
            .isInstanceOf(SchemaLoadingException::class.java)
            .hasMessage("could not read schema from URI \"classpath://.non-existent.file\"")
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
                    <?xml version="1.0">
                    <project>
                    </project>
                """.trimIndent()
            ))
        )

        try {
            subject.load()
            fail("did not throw exception")
        } catch (ex: AggregateSchemaLoadingException) {
            ex.causes.forEach { println(it.javaClass.simpleName) }

            ex.printStackTrace(System.out)
        }
    }
}
