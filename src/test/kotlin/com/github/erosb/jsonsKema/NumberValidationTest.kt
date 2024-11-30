package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NumberValidationTest {

    @Test
    fun minimum() {
        val subject = MinimumSchema(10.0, UnknownSource)
        val instance = JsonNumber(1)
        val actual = Validator.forSchema(subject).validate(instance)!!
        assertEquals(MinimumValidationFailure(subject, instance, JsonPointer("minimum")), actual)
        assertEquals("1 is lower than minimum 10.0", actual.message)
    }

    @Test
    fun maximumFailure() {
        val subject = MaximumSchema(20.0, UnknownSource)
        val instance = JsonNumber(21)
        val actual = Validator.forSchema(subject).validate(instance)!!
        assertEquals(MaximumValidationFailure(subject, instance), actual)
        assertEquals("21 is greater than maximum 20.0", actual.message)
    }
}
