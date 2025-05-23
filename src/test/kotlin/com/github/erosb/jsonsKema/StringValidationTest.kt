package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringValidationTest {

    @Test
    fun test() {
        val schema = SchemaLoader(JsonParser("""
            {
                "allOf": [
                    { "type": "string" },
                    { "$ref": "#/$defs/Details" }
                ],
                "$defs": {
                    "Details": {
                        "minLength": 5,
                        "maxLength": 3,
                        "pattern": "^a*$",
                        "format": "email"
                    }
                }
            }
        """.trimIndent())())()

        val actual = Validator.forSchema(schema).validate(JsonString("bbbb", UnknownSource))!!

        val detailsFailure = actual.causes.single()
        val minLengthFailure = detailsFailure.causes.filterIsInstance<MinLengthValidationFailure>().single()
        assertEquals("#/allOf/1/$ref/minLength", minLengthFailure.dynamicPath.pointer.toString())

        val maxLengthFailure = detailsFailure.causes.filterIsInstance<MaxLengthValidationFailure>().single()
        assertEquals("#/allOf/1/$ref/maxLength", maxLengthFailure.dynamicPath.pointer.toString())

        val patternFailure = detailsFailure.causes.filterIsInstance<PatternValidationFailure>().single()
        assertEquals("#/allOf/1/$ref/pattern", patternFailure.dynamicPath.pointer.toString())
    }
}
