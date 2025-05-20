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
        assertEquals("#/allOf", allOfFailure.dynamicPath.pointer.toString())
        val anyOfFailure = allOfFailure.causes.single() as AnyOfValidationFailure
        assertEquals("#/allOf/0/anyOf", anyOfFailure.dynamicPath.pointer.toString())
        val oneOfFailure = anyOfFailure.causes.single() as OneOfValidationFailure
        assertEquals("#/allOf/0/anyOf/0/oneOf", oneOfFailure.dynamicPath.pointer.toString())
        val notFailure = oneOfFailure.causes.single() as NotValidationFailure
        assertEquals("#/allOf/0/anyOf/0/oneOf/0/not", notFailure.dynamicPath.pointer.toString())
    }
}
