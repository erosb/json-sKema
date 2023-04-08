package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

fun createSchemaLoaderForString(schemaJson: String, remoteDocuments: Map<String, String> = mapOf()): SchemaLoader {
    val client = TestingSchemaClient()
    remoteDocuments.forEach { (uri, json) ->
        client.defineResource(URI(uri), json)
    }
    return SchemaLoader(schemaJson = JsonParser(schemaJson)(), config = SchemaLoaderConfig(client))
}

class RefResolutionTest {

    @Test
    fun `$anchor ref intra-document`() {
        val root: CompositeSchema = createSchemaLoaderForString(
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
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "title"))!!
        assertThat(actual).isEqualTo("my title")
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

        val referred = actual.accept(TraversingSchemaVisitor<CompositeSchema>("$ref"))!!
        assertThat(referred).isSameAs(actual)
    }

    @Test
    fun `$ref references external schema root`() {
        val actual = createSchemaLoaderForString(
            """
                {
                    "$ref": "http://example.com/schema"
                }
            """,
            mapOf(
                Pair(
                    "http://example.com/schema",
                    """
                                {"title": "remote schema"}
                            """
                )
            )
        )() as CompositeSchema

        val referred = (actual.subschemas.iterator().next() as ReferenceSchema).referredSchema as CompositeSchema
        assertThat(referred.title!!.value).isEqualTo("remote schema")
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
            """,
            mapOf(
                Pair(
                    "http://example.org/path/other.json",
                    """
                        {
                            "title": "referred schema"
                        }
                    """
                )
            )
        )()

        val ref: String = root.accept(TraversingSchemaVisitor("additionalProperties", "$ref", "title"))!!
        assertThat(ref).isEqualTo("referred schema")
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
        """,
            mapOf(
                Pair(
                    "http://example.org/path/other.json",
                    """
                        {
                            "title": "referred schema",
                            "$ref": "http://example.org/root.json#"
                        }
        """
                )
            )
        )()
        val referred = root.accept(TraversingSchemaVisitor<CompositeSchema>("additionalProperties", "$ref"))!!
        assertThat(referred.title!!.value).isEqualTo("referred schema")

        val secondRef = referred.accept(TraversingSchemaVisitor<CompositeSchema>("$ref"))!!
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
        """,
            mapOf(
                Pair(
                    "http://remote",
                    """
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
            root.accept(TraversingSchemaVisitor<String>("$ref", "title"))!!
        assertThat(actualMySubschemaTitle).isEqualTo("MySubschema title")
    }

    @Test
    fun `no explicit $id in remote`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$id": "http://original"
                "$ref": "http://remote#myAnchor"
            }
        """,
            mapOf(
                Pair(
                    "http://remote",
                    """
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
        val actualMySubschemaTitle = root.accept(TraversingSchemaVisitor<String>("$ref", "title"))!!
        assertThat(actualMySubschemaTitle).isEqualTo("MySubschema title")
    }

    @Test
    fun `intra-document json pointer lookup`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$ref": "#/$defs/MySchema",
                "$defs": {
                    "MySchema": {
                        "title": "my schema"
                    }
                }
            }
        """
        )() as CompositeSchema
        val actual = root.accept(TraversingSchemaVisitor<CompositeSchema>("$ref"))!!

        assertThat(actual.title!!.value).isEqualTo("my schema")
    }

    @Test
    fun `json pointer escaping`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$ref": "#/$defs/%25child~0node/My~1Subschema",
                "$defs": {
                    "%child~node": {
                        "My/Subschema": {
                            "title": "my title"
                        }
                    }
                }
            }
        """
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "title"))!!
        assertThat(actual).isEqualTo("my title")
    }

    @Test
    fun `json pointer lookup in remote document`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$ref": "http://remote#/$defs/entryPoint"
            }
        """,
            mapOf(
                Pair(
                    "http://remote",
                    """
            {
                "$defs": {
                    "entryPoint": {
                        "$ref": "#/$defs/referred"
                    },
                    "referred": {
                        "title": "my title"
                    }
                }
            }
                    """.trimIndent()
                )
            )
        )()

        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "$ref", "title"))
        assertThat(actual).isEqualTo("my title")
    }

    @Test
    fun `anchor lookup in remote schema`() {
        val root = createSchemaLoaderForString(
            """
            {"$ref": "http://remote"}
        """,
            mapOf(
                Pair(
                    "http://remote",
                    """
            {
                "$ref": "#MyRootSchema",
                "$defs": {
                    "my-root-schema": {
                        "$anchor": "MyRootSchema",
                        "title": "my title"
                    }
                }
            }
        """
                )
            )
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "$ref", "title"))
        assertThat(actual).isEqualTo("my title")
    }

    @Test
    fun `json pointer lookup in remote compound schema`() {
        val root = createSchemaLoaderForString(
            """
            {"$ref": "https://remote"}
        """,
            mapOf(
                Pair(
                    "https://remote",
                    """
            {
                "$id": "https://remote#",
                "$ref": "https://compound-root/my-domain.json#/$defs/MySchema",
                "$defs": {
                    "my-root-schema": {
                        "$id": "https://compound-root",
                        "$defs": {
                            "MyDomain": {
                                "$id": "/my-domain.json",
                                "$defs": {
                                    "MySchema": {
                                        "title": "my title"
                                    }
                                }
                            }
                        }
                        "$anchor": "MyRootSchema",
                        "title": "my root schema title"
                    }
                }
            }
        """
                )
            )
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "$ref", "title"))
        assertThat(actual).isEqualTo("my title")
    }

    @Test
    fun `json pointer in remote, remote root $id mismatches source URI`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$ref": "https://remote"
            }
        """,
            mapOf(
                Pair(
                    "https://remote",
                    """
            {
                "$id": "https://mismatching-remote",
                "$ref": "https://mismatching-remote#MySchema"
                "$defs": {
                    "MySchema": {
                        "$anchor": "MySchema",
                        "title": "my title"
                    }
                }
            }
        """
                )
            )
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "$ref", "title"))!!
        assertThat(actual).isEqualTo("my title")
    }

    @Test
    fun `json pointer base URI change in a child of containingRoot`() {
        val root = createSchemaLoaderForString(
            """
            {"$ref": "https://remote#/$defs/parent/properties/child/MySchema"}
        """,
            mapOf(
                Pair(
                    "https://remote",
                    """
            {
                "$defs" : {
                    "parent": {
                        "$id": "/my-domain.json"
                        "properties": {
                            "child": {
                                "MySchema": {
                                    "$ref": "#other"
                                }
                            },
                            "other": {
                                "$anchor": "other",
                                "title": "my title"
                            }
                        }
                    },
                    "other": {
                        "$anchor": "other",
                        "title": "wrong result"
                    }
                }
            }
        """
                )
            )
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "$ref", "title"))!!
        assertThat(actual).isEqualTo("my title")
    }

    @Test @Disabled
    fun `$dynamicAnchor can be referred by $ref`() {
        val root = createSchemaLoaderForString(
            """
            {
                "$ref": "#dyn-ref"
                "$defs": {
                    "DynRefSchema": {
                        "$dynamicAnchor": "dyn-ref",
                        "title": "my title"
                    }
                }
            }
            """
        )()
        val actual = root.accept(TraversingSchemaVisitor<String>("$ref", "title"))!!
        assertThat(actual).isEqualTo("my title")
    }
}
