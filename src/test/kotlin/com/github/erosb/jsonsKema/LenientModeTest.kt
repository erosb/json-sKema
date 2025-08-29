package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LenientModeTest {

    @Test
    fun `expected boolean, actual string`() {
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

        val actual = Validator.create(
            schema, ValidatorConfig(
                primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
            )
        ).validate(
            """
            "50"
        """.trimIndent()
        )
        assertThat(actual!!.causes).hasSize(2)
    }

    @Test
    fun `expected string, actual boolean, validation happens`() {
        val schema = SchemaLoader("""
            {
               "type": "string",
               "minLength": 8,
               "maxLength": 3
            }
        """.trimIndent())()

        val actual = Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("false")

        assertThat(actual!!.causes).hasSize(2)
    }

    @Test
    fun `expected string, actual number`() {
        val schema = SchemaLoader("""
            {
               "type": "string",
               "minLength": 8,
               "maxLength": 3
            }
        """.trimIndent())()

        val actual = Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("12345")

        assertThat(actual!!.causes).hasSize(2)
    }

    @Test
    fun `expected integer, actual string`() {
            val schema = SchemaLoader("""
            {
                "type": "integer",
                "minimum": 5,
                "maximum": 3
            }
        """.trimIndent())()

        val actual = Validator.create(
            schema, ValidatorConfig(
                primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
            )
        ).validate(
            """
            "4"
        """.trimIndent()
        )
        assertThat(actual!!.causes).hasSize(2)
    }

    @Test
    fun `optional properties can be null`() {
        val schema = SchemaLoader("""
            {
                "type": "object",
                "required": ["x"],
                "properties": {
                    "x": {
                        "type": "number"
                    },
                    "y": {
                      "type": "string"  
                    }
                }
            }
        """.trimIndent())()

        val actual = Validator.create(schema, ValidatorConfig(
            primitiveValidationStrategy = PrimitiveValidationStrategy.LENIENT
        )).validate("""
            {"x": 2, "y": null}
        """.trimIndent())

        assertNull(actual)
    }
}
