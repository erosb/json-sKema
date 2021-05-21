package com.github.erosb.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI


fun createSchemaLoaderForString(schemaJson: String, remoteDocuments: Map<String, String> = mapOf()): SchemaLoader {
    val client = TestingSchemaClient()
    remoteDocuments.forEach { (uri, json) ->
        client.defineResource(URI(uri), json)
    }
    return SchemaLoader(schemaJson = JsonParser(schemaJson)(), config = SchemaLoaderConfig(client))
}

class ReferenceResolutionTest {

    @Test
    fun `$anchor ref intra-document`() {
        val actual: CompositeSchema = createSchemaLoaderForString(
            """
            {
                "$ref": "#myAnchor",
                "$defs": {
                   "ddd": {
                        "title": "my title",
                        "$anchor": "myAnchor"
                   }
                }
            }
        """.trimIndent()
        )() as CompositeSchema
        val referred = actual.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema as CompositeSchema
        assertThat("my title").isEqualTo(referred.title!!.value)
    }

    @Test
    fun `$ref is #`() {
        val actual = createSchemaLoaderForString(
            """
            {
              "title": "root schema",
              "allOf": [
                {
                  "title": "subschema 0",
                  "$ref": "#"
                }
              ]
            }
        """.trimIndent()
        )() as CompositeSchema
        assertThat(actual.title!!.value).isEqualTo("root schema")
        val allOf = actual.subschemas.iterator().next() as AllOfSchema
        val combinedContainingRef = allOf.subschemas[0] as CompositeSchema
        assertThat(combinedContainingRef.title!!.value).isEqualTo("subschema 0")
        val refSchema = combinedContainingRef.subschemas.iterator().next() as ReferenceSchema
        assertThat(refSchema).isEqualTo(ReferenceSchema(actual, "#", SourceLocation(6, 7, pointer("allOf", "0", "\$ref"))))
    }

    @Test
    fun `$ref references root by base URI as $id`() {
        val actual: CompositeSchema = createSchemaLoaderForString(
            """
                {
                    "title": "root schema",
                    "$id": "http://example.org/schema",
                    "$ref": "http://example.org/schema"
                }
            """.trimIndent()
        )() as CompositeSchema

        val refSchema = actual.subschemas.iterator().next() as ReferenceSchema
        assertThat(refSchema.referredSchema).isSameAs(actual)
    }
    
    @Test
    fun `$ref references root with empty fragment`() {
        val actual: CompositeSchema = createSchemaLoaderForString(
            """
                {
                    "title": "root schema",
                    "$id": "http://example.org/schema",
                    "$ref": "http://example.org/schema#"
                }
            """.trimIndent()
        )() as CompositeSchema
        
        val referred = actual.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema
        assertThat(referred).isSameAs(actual)
    }

    @Test
    fun `$ref references external schema root`() {
        val actual = createSchemaLoaderForString(
            """
                {
                    "$ref": "http://example.com/schema"
                }
            """, mapOf(
                Pair(
                    "http://example.com/schema", """
                                {"title": "remote schema"}
                            """
                )
            )
        )() as CompositeSchema

        val referred = (actual.subschemas.iterator().next() as ReferenceSchema).referredSchema as CompositeSchema
        assertThat(referred.title!!.value).isEqualTo("remote schema");
    }

    @Test
    fun `base URI alteration with relative URI`() {
        val root = createSchemaLoaderForString(
            """
                {
                    "$id": "http://example.org/root.json",
                    "additionalProperties": {
                        "$id": "http://example.org/path/",
                        "$ref": "other.json"
                    }
                }
            """, mapOf(
                Pair(
                    "http://example.org/path/other.json", """
                        {
                            "title": "referred schema"
                        }
                    """
                )
            )
        )()

        val ref: ReferenceSchema = root.accept(TraversingVisitor("additionalProperties", "$ref"))!!
        val referred: CompositeSchema = ref.referredSchema as CompositeSchema
        assertThat(referred.title!!.value).isEqualTo("referred schema");
    }

    @Test
    fun `recursive use of $ref`() {
        val root = createSchemaLoaderForString(
            """
            {
                    "$id": "http://example.org/root.json",
                    "additionalProperties": {
                        "$id": "http://example.org/path/",
                        "$ref": "other.json"
                    }
                }
        """, mapOf(
                Pair(
                    "http://example.org/path/other.json", """
                        {
                            "title": "referred schema",
                            "$ref": "http://example.org/root.json#"
                        }
        """
                )
            )
        )()
        val referred =
            root.accept(TraversingVisitor<ReferenceSchema>("additionalProperties", "$ref"))!!.referredSchema as CompositeSchema
        assertThat(referred.title!!.value).isEqualTo("referred schema")

        val secondRef = referred.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema
        assertThat(secondRef).isSameAs(root)
    }

    @Test
    fun `$anchor resolution without $id`() {
        val root = createSchemaLoaderForString(
                """
            {
                "$id": "http://example.org/",
                "$ref": "http://example.org/bar#foo",
                "$defs": {
                    "A": {
                        "$id": "bar",
                        "$anchor": "foo"
                    }
                }
            }
                """.trimIndent()
            )()
    }

    @Test
    fun `$anchor resolution in remote`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$id": "http://original"
                "$ref": "http://remote#myAnchor"
            }
        """, mapOf(
                Pair(
                    "http://remote", """
                {
                    "$id": "http://remote",
                    "title": "remote root title",
                    "$defs": {
                        "MySubschema": {
                            "$anchor": "myAnchor",
                            "title": "MySubschema title"
                        }
                    }
                }
            """
                )
            )
        )()
        val actualMySubschemaTitle =
            (root.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema as CompositeSchema).title;

        assertThat(actualMySubschemaTitle!!.value).isEqualTo("MySubschema title")
    }

    @Test
    fun `no explicit $id in remote`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$id": "http://original"
                "$ref": "http://remote#myAnchor"
            }
        """, mapOf(
                Pair(
                    "http://remote", """
                {
                    "title": "remote root title",
                    "$defs": {
                        "MySubschema": {
                            "$anchor": "myAnchor",
                            "title": "MySubschema title"
                        }
                    }
                }
        """
                )
            )
        )()
        val actualMySubschemaTitle =
            (root.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema as CompositeSchema).title;

        assertThat(actualMySubschemaTitle!!.value).isEqualTo("MySubschema title")
    }

    @Test
    fun `intra-document json pointer lookup`() {
        val root = createSchemaLoaderForString("""
            {
                "$ref": "#/$defs/MySchema",
                "$defs": {
                    "MySchema": {
                        "title": "my schema"
                    }
                }
            }
        """)() as CompositeSchema
        val actual = root.accept(TraversingVisitor<ReferenceSchema>("$ref"))!!.referredSchema as CompositeSchema
        
        assertThat(actual.title!!.value).isEqualTo("my schema")
    }
    
    @Test
    fun `json pointer escaping`(){
        mapOf(Pair("", ""))
    }
}
