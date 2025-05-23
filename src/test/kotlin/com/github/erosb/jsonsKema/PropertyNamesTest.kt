package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertyNamesTest {

    @Test
    fun test() {
        val schema = SchemaLoader(JsonParser("""
            {
                "propertyNames": {"maxLength": 2}
            }
        """.trimIndent())())()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {"aaa":"x"}
        """.trimIndent())()) as PropertyNamesValidationFailure
        assertEquals("#/propertyNames", actual.dynamicPath.pointer.toString())
        val cause = actual.causesByProperties.values.first() as MaxLengthValidationFailure
        assertEquals("#/propertyNames/maxLength", cause.dynamicPath.pointer.toString())
    }
}
