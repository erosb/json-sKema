package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicatorValidationTest {

    @Test
    fun `dynamicPaths are returned`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "allOf": [
                    {
                        "anyOf": [
                            {
                                "oneOf": [
                                    {
                                        "not": true
                                    }       
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent())())()

        val allOfFailure = Validator.forSchema(schema).validate(JsonParser("4")()) as AllOfValidationFailure
        println(allOfFailure)
        assertEquals("#/allOf", allOfFailure.dynamicPath.toString())
        val anyOfFailure = allOfFailure.causes.single() as AnyOfValidationFailure
        assertEquals("#/allOf/anyOf", anyOfFailure.dynamicPath.toString())
        val oneOfFailure = anyOfFailure.causes.single() as OneOfValidationFailure
        assertEquals("#/allOf/anyOf/oneOf", oneOfFailure.dynamicPath.toString())
        val notFailure = oneOfFailure.causes.single() as NotValidationFailure
        assertEquals("#/allOf/anyOf/oneOf/not", notFailure.dynamicPath.toString())
    }
}
