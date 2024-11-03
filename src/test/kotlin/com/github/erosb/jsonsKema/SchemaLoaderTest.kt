package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.net.URI

val id = "\$id"
val ref = "\$ref"
val defs = "\$defs"
val anchor = "\$anchor"
val dynamicRef = "\$dynamicRef"
val dynamicAnchor = "\$dynamicAnchor"

class TestingSchemaClient : SchemaClient {

    private val resources: MutableMap<URI, String> = mutableMapOf()

    override fun get(uri: URI): InputStream {
        if (resources.containsKey(uri)) {
            return ByteArrayInputStream(resources[uri]!!.toByteArray())
        }
        throw UncheckedIOException(IOException("URI $uri not found"))
    }

    fun defineResource(uri: URI, remoteSchema: String): TestingSchemaClient {
        resources[uri] = remoteSchema
        return this
    }
}

class SchemaLoaderTest {

    @Test
    fun `loads true schema`() {
        val underTest = createSchemaLoaderForString("true")
        assertThat(underTest()).isEqualTo(TrueSchema(SourceLocation(1, 1, pointer(), DEFAULT_BASE_URI)))
    }

    @Test
    fun `loads false schema`() {
        val underTest = createSchemaLoaderForString("false")
        assertThat(underTest()).isEqualTo(FalseSchema(SourceLocation(1, 1, pointer(), DEFAULT_BASE_URI)))
    }

    @Test
    fun `loads minLength schema`() {
        val underTest = createSchemaLoaderForString(
            """
            { "minLength": 20}
            """.trimIndent()
        )
        assertThat(underTest()).isEqualTo(
            CompositeSchema(
                setOf(
                    MinLengthSchema(20, SourceLocation(1, 3, pointer("minLength"), DEFAULT_BASE_URI))
                ),
                SourceLocation(1, 1, pointer(), DEFAULT_BASE_URI)
            )
        )
    }

    @Test
    fun `loads maxLength schema`() {
        val underTest = createSchemaLoaderForString(
            """
            { "maxLength": 20}
            """.trimIndent()
        )
        assertThat(underTest()).isEqualTo(
            CompositeSchema(
                setOf(
                    MaxLengthSchema(20, SourceLocation(1, 3, pointer("maxLength"), DEFAULT_BASE_URI))
                ),
                SourceLocation(1, 1, pointer(), DEFAULT_BASE_URI)
            )
        )
    }

    @Test
    fun `basic metadata loading`() {
        val actual = createSchemaLoaderForString(
            """
            {
                "title": "My title",
                "description": "My description",
                "deprecated": false,
                "default": null,
                "if": false,
                "then": true,
                "dummy": "hi"
            }
            """.trimIndent()
        )()
        val expected = CompositeSchema(
            subschemas = setOf(
                IfThenElseSchema(
                    FalseSchema(SourceLocation(8, 11, pointer("#/if"), DEFAULT_BASE_URI)),
                    TrueSchema(SourceLocation(9, 13, pointer("#/then"), DEFAULT_BASE_URI)),
                    null,
                    SourceLocation(8, 5, pointer("#/if"), DEFAULT_BASE_URI))
            ),
            location = UnknownSource,
            title = JsonString("My title"),
            description = JsonString("My description"),
            deprecated = JsonBoolean(false),
            default = JsonNull(),
            unprocessedProperties = mutableMapOf(JsonString("dummy") to JsonString("hi"))
        )
        assertThat(actual).usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }

    @Test
    fun `obsolete use of items`() {
        val exception = assertThrows(JsonTypeMismatchException::class.java) {
            println(createSchemaLoaderForString(
                """
                {
                    "type": "object",
                    "properties": {
                        "prop": { 
                            "type": "array", 
                            "title": "array desc", 
                            "items": [ {"type": "object", "title": "some obj"} ] 
                        } 
                    }
                }
                """.trimIndent()
            )())
        }
        assertThat(exception.message).contains("boolean or object")
    }

    @Test
    fun `empty fragment in $schema`() {
        SchemaLoader("""
            {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "properties": {
                "a": {
                    "type": "number"
                }
              }
            }
        """.trimIndent())()
    }
}
