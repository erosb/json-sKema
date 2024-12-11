package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test

class Issue32Test {

    @Test
    fun test() {
        val schema = SchemaLoader("""
            {
              "$schema": "https://json-schema.org/draft/2019-09/schema",
              "if": {
                "properties": {
                  "type": {
                    "const": "business"
                  }
                },
                "required": [
                  "type"
                ],
                "type": "object"
              },
              "properties": {
                "city": {
                  "type": "string"
                },
                "state": {
                  "type": "string"
                },
                "street_address": {
                  "type": "string"
                },
                "type": {
                  "enum": [
                    "residential",
                    "business"
                  ]
                }
              },
              "required": [
                "street_address",
                "city",
                "state",
                "type"
              ],
              "then": {
                "properties": {
                  "department": {
                    "type": "string"
                  }
                }
              },
              "title": "MyTestSchema",
              "type": "object",
              "unevaluatedProperties": false
            }

        """.trimIndent())()

        val actual= Validator.forSchema(schema).validate("""
            {
              "street_address": "1600 Pennsylvania Avenue NW",
              "city": "Washington",
              "state": "DC",
              "type": "residential",
              "department": "HR"
            }

        """.trimIndent()) as UnevaluatedPropertiesValidationFailure
        println(actual)
    }
}
