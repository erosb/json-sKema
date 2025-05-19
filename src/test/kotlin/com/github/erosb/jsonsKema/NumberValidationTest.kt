package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NumberValidationTest {

    @Test
    fun minimum() {
        val subject = MinimumSchema(10.0, UnknownSource)
        val instance = JsonNumber(1)
        val actual = Validator.forSchema(subject).validate(instance) as MinimumValidationFailure
        assertEquals(MinimumValidationFailure(subject, instance, UnknownSource + "minimum"), actual)
        assertEquals("1 is lower than minimum 10.0", actual.message)
        assertEquals("#/minimum", actual.dynamicPath.toString())
    }

    @Test
    fun maximumFailure() {
        val subject = MaximumSchema(20.0, UnknownSource)
        val instance = JsonNumber(21)
        val actual = Validator.forSchema(subject).validate(instance) as MaximumValidationFailure
        assertEquals(MaximumValidationFailure(subject, instance, UnknownSource + "maximum"), actual)
        assertEquals("21 is greater than maximum 20.0", actual.message)
        assertEquals("#/maximum", actual.dynamicPath.toString())
    }

    @Test
    fun exclusiveMaximumFailure() {
        val subject = ExclusiveMaximumSchema(20.0, UnknownSource)
        val instance = JsonNumber(20)
        val actual = Validator.forSchema(subject).validate(instance) as ExclusiveMaximumValidationFailure
        assertEquals(ExclusiveMaximumValidationFailure(subject, instance, UnknownSource + "exclusiveMaximum"), actual)
        assertEquals("20 is greater than or equal to maximum 20.0", actual.message)
        assertEquals("#/exclusiveMaximum", actual.dynamicPath.toString())
    }

    @Test
    fun exclusiveMinimumFailure() {
        val subject = ExclusiveMinimumSchema(20.0, UnknownSource)
        val instance = JsonNumber(20)
        val actual = Validator.forSchema(subject).validate(instance) as ExclusiveMinimumValidationFailure
        assertEquals(ExclusiveMinimumValidationFailure(subject, instance, UnknownSource + "exclusiveMinimum"), actual)
        assertEquals("20 is lower than or equal to minimum 20.0", actual.message)
        assertEquals("#/exclusiveMinimum", actual.dynamicPath.toString())
    }

    @Test
    fun multipleOf() {
        val subject = MultipleOfSchema(60, UnknownSource)
        val instance = JsonNumber(20)
        val actual = Validator.forSchema(subject).validate(instance) as MultipleOfValidationFailure
        assertEquals(MultipleOfValidationFailure(subject, instance, UnknownSource + "multipleOf"), actual)
        assertEquals("20 is not a multiple of 60", actual.message)
        assertEquals("#/multipleOf", actual.dynamicPath.toString())
    }
}
