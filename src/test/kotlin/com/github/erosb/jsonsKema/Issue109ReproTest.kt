package com.github.erosb.jsonsKema

import com.github.erosb.jsonsKema.Validator.Companion.forSchema
import org.junit.jupiter.api.Test

class Issue109ReproTest {

    @Test
    fun test() {
        val schemaJson = JsonParser(
            """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "aaa": {
              "type": "object",
              "properties": {
                "aaa1": {
                  "type": "object",
                  "properties": {
                    "code": {
                      "type": "string"
                    }
                  },
                  "additionalProperties": false
                },
                "aaa2": {
                  "type": "object",
                  "properties": {
                    "code": {
                      "type": "string"
                    }
                  },
                  "additionalProperties": false
                }
              },
              "required": [
                "aaa1",
                "aaa2"
              ]
            },
            "bbb": {
              "type": "object",
              "properties": {
                "code": {
                  "type": "string"
                }
              },
              "additionalProperties": false
            }
          }
        }
        
        """.trimIndent()
        ).parse()
        val schema = SchemaLoader(schemaJson).load()
        val validator = forSchema(schema)
        val instance = JsonParser(
            """
        {
            "aaa": {
                "aaa1": {
                    "codeERROR": "AAA1"
                },
                "aaa2": {
                    "codeERROR": "AAA2"
                }
            },
            "bbb": {
                "codeERROR": "BBB"
            }
        }
        
        """.trimIndent()
        ).parse()

        val failure = validator.validate(instance)
        println(failure)
    }
}
