package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnevaluatedPropsItemsTest {

    @Test
    fun `dynamic path for unevaluatedProps`() {
        val schema = SchemaLoader("""
            {
                "unevaluatedProperties": {
                    "type": "object",
                    "required": ["reason"]
                },
                "properties": {
                    "a": {"type": "string"}
                }
            }
        """.trimIndent())()

        val actual = Validator.forSchema(schema).validate("""
            {
                "x": {}
            }
        """.trimIndent()) as UnevaluatedPropertiesValidationFailure

        assertEquals("#/unevaluatedProperties", actual.dynamicPath.pointer.toString())

        val cause = actual.causes.single() as RequiredValidationFailure
        assertEquals("#/unevaluatedProperties/required", cause.dynamicPath.pointer.toString())
    }

    @Test
    fun `dynamic path for unevaluatedItems`() {
        val schema = SchemaLoader("""
            {
                "unevaluatedItems": {
                    "enum": [null]
                },
                "prefixItems": [
                    {"type": "string"}
                ]
            }
        """.trimIndent())()

        val actual = Validator.forSchema(schema).validate("""
            ["a", "b"]
        """.trimIndent()) as UnevaluatedItemsValidationFailure

        assertEquals("#/unevaluatedItems", actual.dynamicPath.pointer.toString())

        val cause = actual.causes.single() as EnumValidationFailure
        assertEquals("#/unevaluatedItems/enum", cause.dynamicPath.pointer.toString())
    }
}
