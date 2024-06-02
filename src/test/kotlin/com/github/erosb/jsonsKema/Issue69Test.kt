package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test

class Issue69Test {

    @Test
    fun test() {
        val instance = JsonParser("""
            {
                "a": 1, "b": 2, "c": 3, "d": 4            
            }
        """.trimIndent())()

        val schemaJson = JsonParser("""
            {
                "allOf": [
                    {
                        "properties": {"a": {"type": "string"}}
                    }
                ],
                "unevaluatedProperties": { "type": "boolean" }
            }
        """.trimIndent())()

        val schema = SchemaLoader(schemaJson)()

        val actual = Validator.forSchema(schema).validate(instance)
        println(actual)

    }
}
