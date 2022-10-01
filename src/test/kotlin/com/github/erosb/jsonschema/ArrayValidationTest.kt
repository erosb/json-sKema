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

    @Test
    fun `items failure`() {
        val itemsSchema = TypeSchema(JsonString("boolean"), UnknownSource)
        val schema = ItemsSchema(itemsSchema, UnknownSource)
        val first = JsonString("asd")
        val second = JsonString("bsd")
        val instance = JsonArray(listOf(JsonBoolean(true), first, second))
        val expected = ItemsValidationFailure(
            mapOf(
                1 to TypeValidationFailure("string", itemsSchema, first),
                2 to TypeValidationFailure("string", itemsSchema, second)
            ),
            schema,
            instance
        )
        val actual = Validator.forSchema(schema).validate(instance)
        assertEquals(expected, actual)
        assertEquals("array items 1, 2 failed to validate against \"items\" subschema", actual?.message)
    }

    @Test
    fun `contains failure`() {
    }
}
