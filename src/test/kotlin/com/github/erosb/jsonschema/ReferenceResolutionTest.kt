package com.github.erosb.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class ReferenceResolutionTest {

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

    @Test
    fun `base URI alteration with relative URI`() {
        val root = SchemaLoader(schemaJson = JsonParser("""
                {
                    "$id": "http://example.org/root.json",
                    "additionalProperties": {
                        "$id": "http://example.org/path/",
                        "$ref": "other.json"
                    }
                }
            """.trimIndent())(), config = SchemaLoaderConfig(TestingSchemaClient()
                .defineResource(URI("http://example.org/path/other.json"), """
                        {
                            "title": "referred schema"
                        }
                    """.trimIndent())))() as CompositeSchema
        
        val ref: ReferenceSchema =  root.accept(TraversingVisitor("additionalProperties", "$ref"))!!
        val referred: CompositeSchema = ref.referredSchema as CompositeSchema
        assertThat(referred.title!!.value).isEqualTo("referred schema");
    }
    
    @Test
    fun `recursive use of $ref`() {
        val actual = SchemaLoader(schemaJson = JsonParser("""
                {
                    "$id": "http://example.org/root.json",
                    "additionalProperties": {
                        "$id": "http://example.org/path/",
                        "$ref": "other.json"
                    }
                }
            """.trimIndent())(), config = SchemaLoaderConfig(TestingSchemaClient()
                .defineResource(URI("http://example.org/path/other.json"), """
                        {
                            "title": "referred schema",
                            "$ref": "http://example.org/root.json"
                        }
                    """.trimIndent())))() as CompositeSchema
    }
}
