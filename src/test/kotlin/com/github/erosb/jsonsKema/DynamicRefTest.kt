package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DynamicRefTest {

    @Test
    fun testArrayGenerics() {
        val schema = createSchemaLoaderForString(
            """
            {
                "type": "object",
                "properties": {
                    "intList": {
                        "$ref": "#/$defs/List",
                        "$defs": {
                            "elemType": {
                                "$dynamicAnchor": "elemType",
                                "type": "integer"
                            }
                        }
                    }
                }
                "$defs": {
                    "List": {
                        "type": "array",
                        "items": {
                            "$dynamicRef": "#elemType"
                        }
                    }
                }
            }
            """.trimIndent()
        )()
        val actual = Validator.forSchema(schema).validate(
            JsonParser(
                """
            {
                "intList": [false]
            }
        """
            )()
        )

        println(actual)
        assertThat(actual).isNotNull()
    }

    @Test
    fun dynamicRef_json_Line_113() {
        val schema = createSchemaLoaderForString(
            """
            {
            "$id": "https://test.json-schema.org/typical-dynamic-resolution/root",
            "$ref": "list",
            "$defs": {
                "foo": {
                    "$dynamicAnchor": "items",
                    "type": "string"
                },
                "list": {
                    "$id": "list",
                    "type": "array",
                    "items": { "$dynamicRef": "#items" },
                    "$defs": {
                      "items": {
                          "$dynamicAnchor": "items"
                      }
                    }
                }
            }
        }
            """.trimIndent()
        )()
        val actual = Validator.forSchema(schema).validate(
            JsonParser(
                """
                    ["foo", 42]
        """
            )()
        )

        println(actual)
        assertThat(actual).isNotNull()
    }
}
