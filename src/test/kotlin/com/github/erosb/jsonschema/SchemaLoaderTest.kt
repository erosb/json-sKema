package com.github.erosb.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.*
import java.net.URI

val id = "\$id"
val ref = "\$ref"

class TestingSchemaClient : SchemaClient {

    private val resources: MutableMap<URI, String> = mutableMapOf()

    override fun get(uri: URI): InputStream {
        if (resources.containsKey(uri)) {
            return ByteArrayInputStream(resources[uri]!!.toByteArray())
        }
        throw UncheckedIOException(IOException("URI $uri not found"));
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
        assertThat(underTest()).isEqualTo(TrueSchema(SourceLocation(1, 1, pointer())))
    }

    @Test
    fun `loads false schema`() {
        val underTest = createSchemaLoaderForString("false");
        assertThat(underTest()).isEqualTo(FalseSchema(SourceLocation(1, 1, pointer())))
    }

    @Test
    fun `loads minLength schema`() {
        val underTest = createSchemaLoaderForString("""
            { "minLength": 20}
        """.trimIndent())
        assertThat(underTest()).isEqualTo(CompositeSchema(setOf(
                MinLengthSchema(20, SourceLocation(1, 3, pointer("minLength")))
        ),
                SourceLocation(1, 1, pointer())
        ))
    }

    @Test
    fun `invalid minLength fractional`() {
        val exc = assertThrows(JsonTypingException::class.java) {
            createSchemaLoaderForString("""
                    { "minLength": 20.0}
                """.trimIndent())()
        }
        assertThat(exc).isEqualTo(JsonTypingException("integer", "number", SourceLocation(1, 16, pointer("minLength"))))
    }

    @Test
    fun `loads maxLength schema`() {
        val underTest = createSchemaLoaderForString("""
            { "maxLength": 20}
        """.trimIndent())
        assertThat(underTest()).isEqualTo(CompositeSchema(setOf(
                MaxLengthSchema(20, SourceLocation(1, 3, pointer("maxLength")))
        ),
                SourceLocation(1, 1, pointer())
        ))
    }

    @Test
    fun `basic metadata loading`() {
        val actual = createSchemaLoaderForString("""
            {
                "title": "My title",
                "description": "My description",
                "writeOnly": false,
                "readOnly": true,
                "deprecated": false,
                "default": null
            }
        """.trimIndent())()
        val expected = CompositeSchema(
                subschemas = emptySet(),
                location = UnknownSource,
                title = JsonString("My title"),
                description = JsonString("My description"),
                readOnly = JsonBoolean(true),
                writeOnly = JsonBoolean(false),
                deprecated = JsonBoolean(false),
                default = JsonNull()
        )
        assertThat(actual).usingRecursiveComparison()
                .ignoringFieldsOfTypes(SourceLocation::class.java)
                .isEqualTo(expected)
    }

    @Nested
    class ReferenceResolution {

        @Test
        fun `$ref is #`() {
            val json = JsonParser(javaClass.getResourceAsStream("ref-resolution.json"))()
            val actual = SchemaLoader(json)() as CompositeSchema
            assertThat(actual.title!!.value).isEqualTo("root schema")
            val allOf = actual.subschemas.iterator().next() as AllOfSchema
            val combinedContainingRef = allOf.subschemas[0] as CompositeSchema
            assertThat(combinedContainingRef.title!!.value).isEqualTo("subschema 0")
            val refSchema = combinedContainingRef.subschemas.iterator().next() as ReferenceSchema
            assertThat(refSchema).isEqualTo(ReferenceSchema(actual, SourceLocation(6, 7, pointer("allOf", "0", "\$ref"))))
        }

        @Test
        fun `$ref references root by base URI as $id`() {
            val actual: CompositeSchema = createSchemaLoaderForString("""
                {
                    "title": "root schema",
                    "$id": "http://example.org/schema",
                    "$ref": "http://example.org/schema"
                }
            """.trimIndent())() as CompositeSchema

            val refSchema = actual.subschemas.iterator().next() as ReferenceSchema
            assertThat(refSchema.referredSchema).isSameAs(actual)
        }

        @Test
        fun `$ref references external schema root`() {
            val actual = SchemaLoader(schemaJson = JsonParser("""
                {
                    "$ref": "http://example.com/schema"
                }
            """.trimIndent())(),
                    config = SchemaLoaderConfig(TestingSchemaClient()
                            .defineResource(URI("http://example.com/schema"), """
                                {"title": "remote schema"}
                            """.trimIndent())
                    ))() as CompositeSchema
            
            val referred = (actual.subschemas.iterator().next() as ReferenceSchema).referredSchema as CompositeSchema
            assertThat(referred.title!!.value).isEqualTo("remote schema");
        }
    }

}
