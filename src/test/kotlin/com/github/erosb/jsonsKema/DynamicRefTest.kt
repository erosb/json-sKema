package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DynamicRefTest {

    @Test
    fun testObjectGenerics() {
        val schema = createSchemaLoaderForString(
            """
            {
                "$id": "https://test.json-schema.org/typical-dynamic-resolution/root",
                "$ref": "#/$defs/Obj",
                "$defs": {
                    "Obj": {
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
                            },
                            "stringList": {
                                "$ref": "#/$defs/List",
                                "$defs": {
                                    "elemType": {
                                        "$dynamicAnchor": "elemType",
                                        "type": "string"
                                    }
                                }
                            }
                        }
                    },
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
        println(schema.subschemas())
        val actual = Validator.forSchema(schema).validate(
            JsonParser(
                """
            {
                "intList": [1, null],
                "stringList": ["str", null]
            }
        """
            )()
        )

        println(actual!!.toJSON())
        assertThat(actual).isNotNull()
    }

    @Test
    fun testArrayGenerics() {
        val schema = createSchemaLoaderForString(
            """
            {
                "$id": "https://test.json-schema.org/typical-dynamic-resolution/root",
                "$ref": "#/$defs/List",
                "$defs": {
                    "elemType": {
                        "$dynamicAnchor": "elemType",
                        "type": "integer"
                    },
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
            [false]
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
