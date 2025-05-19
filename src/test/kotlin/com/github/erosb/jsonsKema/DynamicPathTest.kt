package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DynamicPathTest {

    @Test
    fun minimumTest() {
        val schema = SchemaLoader(JsonParser("""
            {
              "properties": {
                "id": {
                    "$ref": "#/$defs/Id"
                }
              },
              "$defs": {
                "Id": {
                  "minimum": 1
                }
              }
            }
        """.trimIndent())())()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {"id": 0}
        """.trimIndent())()) as MinimumValidationFailure

        Assertions.assertEquals("mem://input#/properties/id/$ref/minimum", actual.dynamicPath.toString())
        println(actual.toJSON())
    }

    @Test
    fun combinators()  {
        val schema = SchemaLoader(JsonParser("""
            {
              "properties": {
                "id": {
                    "allOf": [
                      {
                        "$ref": "#/$defs/Id"
                      }
                    ]
                },
                "name": {
                    "minLength": 2
                },
                "email": {
                    "anyOf": [
                        { "format": "email" }
                    ]
                },
                "age": {
                    "oneOf": [
                      { "type": "integer" }
                    ]
                }
              },
              "$defs": {
                "Id": {
                  "minimum": 1
                }
              }
            }
        """.trimIndent())())()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {"id": 0, "name": "A", "age": 2.4, "email": "aaa"}
        """.trimIndent())()) !!

        println(actual.toJSON())
    }
}
