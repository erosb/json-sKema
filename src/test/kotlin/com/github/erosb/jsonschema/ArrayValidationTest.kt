package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArrayValidationTest {

    @Test
    fun uniqueItems() {
        val schema = UniqueItemsSchema(true, UnknownSource)
        val instance = JsonArray(listOf(JsonNumber(1), JsonNumber(2), JsonNumber(1)))
        val actual = Validator.forSchema(schema).validate(instance)
        val expected = UniqueItemsValidationFailure(listOf(0, 2), schema, instance)
        assertEquals(expected, actual)
        assertEquals("the same array element occurs at positions 0, 2", actual?.message)
    }

    @Test
    fun uniqueItems_false() {
        val schema = UniqueItemsSchema(false, UnknownSource)
        val instance = JsonArray(listOf(JsonNumber(1), JsonNumber(2), JsonNumber(1)))
        val actual = Validator.forSchema(schema).validate(instance)
        assertNull(actual)
    }
}
