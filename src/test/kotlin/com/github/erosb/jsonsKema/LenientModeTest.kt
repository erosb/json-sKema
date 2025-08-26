package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LenientModeTest {

    @Test
    fun test() {
        val schema = SchemaLoader("""
            {
                "type": "array",
                "items": {
                    "type": "boolean"
                }
            }
        """.trimIndent())()

        assertNull(Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("""
            ["true", "No"]
        """.trimIndent()))
    }

    @Test
    fun numberTest() {
        val schema = SchemaLoader("""
            {"type": "number"}
        """.trimIndent())()

        assertNull(Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("""
            "12.34"
        """.trimIndent()))
    }

    @Test
    fun numberTypeFailure() {
        val schema = SchemaLoader("""
            {"type": "number"}
        """.trimIndent())()

        assertNotNull(Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("""
            "not-a-number"
        """.trimIndent()))
    }

    @Test
    fun numberRangeFailure() {
        val schema = SchemaLoader("""
            {
              "type": "number",
              "minimum": 100,
              "maximum": 0
            }
        """.trimIndent())()

        assertNotNull(Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("""
            "50"
        """.trimIndent()))
    }
}
