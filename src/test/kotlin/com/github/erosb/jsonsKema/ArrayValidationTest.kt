package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ArrayValidationTest {

    @Test
    fun uniqueItems() {
        val schema = UniqueItemsSchema(true, UnknownSource)
        val instance = JsonArray(listOf(JsonNumber(1), JsonNumber(2), JsonNumber(1)))
        val actual = Validator.forSchema(schema).validate(instance)
        val expected = UniqueItemsValidationFailure(listOf(0, 2), schema, instance, UnknownSource + "uniqueItems")
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
        val schema = ItemsSchema(itemsSchema, 0, UnknownSource)
        val first = JsonString("asd")
        val second = JsonString("bsd")
        val instance = JsonArray(listOf(JsonBoolean(true), first, second))
        val expected = ItemsValidationFailure(
            mapOf(
                1 to TypeValidationFailure("string", itemsSchema, first, UnknownSource + "items" + "type"),
                2 to TypeValidationFailure("string", itemsSchema, second, UnknownSource + "items" + "type")
            ),
            schema,
            instance,
            UnknownSource + "items"
        )
        val actual = Validator.forSchema(schema).validate(instance)
        assertEquals(expected, actual)
        assertEquals("array items 1, 2 failed to validate against \"items\" subschema", actual?.message)
    }

    @Nested
    inner class ContainsTest {
        val containedSchema = ConstSchema(JsonNumber(5), UnknownSource)

        @Test
        fun `contains only failure`() {
            val schema = ContainsSchema(containedSchema, 1, null, UnknownSource)
            val instance = JsonParser("[1, 2]")().requireArray()

            val actual = Validator.forSchema(schema).validate(instance)!!

            val expected = ContainsValidationFailure(
                "no array items are valid against \"contains\" subschema, expected minimum is 1",
                schema = schema,
                instance = instance,
                dynamicPath = UnknownSource + "contains"
            )
            assertEquals(expected, actual)
        }

        @Test
        fun `minContains violation`() {
            val schema = ContainsSchema(containedSchema, 2, null, UnknownSource)
            val instance = JsonArray(listOf(JsonNumber(5), JsonNumber(3)))

            val actual = Validator.forSchema(schema).validate(instance)!!

            val expected = ContainsValidationFailure("only 1 array item is valid against \"contains\" subschema, expected minimum is 2",
                schema,
                instance,
                UnknownSource + "contains"
            )
            assertEquals(expected, actual)
        }

        @Test
        fun `minContains is 0`() {
            val schema = ContainsSchema(containedSchema, 0, null, UnknownSource)
            val instance = JsonParser("[4]")()

            val actual = Validator.forSchema(schema).validate(instance)

            assertNull(actual)
        }

        @Test
        fun `minContains is 0, instance is empty`() {
            val schema = ContainsSchema(containedSchema, 0, null, UnknownSource)
            val instance = JsonArray(emptyList())

            val actual = Validator.forSchema(schema).validate(instance)

            assertNull(actual)
        }

        @Test
        fun `empty array, minContains 1`() {
            val schema = ContainsSchema(containedSchema, 1, null, UnknownSource)

            val instance = JsonArray(emptyList())

            val actual = Validator.forSchema(schema).validate(instance)
            val expected = ContainsValidationFailure("no array items are valid against \"contains\" subschema, expected minimum is 1",
                schema,
                instance,
                UnknownSource + "contains"
            )

            assertEquals(expected, actual)
        }

        @Test
        fun `maxContains violation`() {
            val schema = ContainsSchema(containedSchema, 0, 1, UnknownSource)

            val instance = JsonArray(listOf(JsonNumber(5), JsonNumber(5)))

            val actual = Validator.forSchema(schema).validate(instance)
            val expected = ContainsValidationFailure("2 array items are valid against \"contains\" subschema, expected maximum is 1",
                schema,
                instance,
                UnknownSource + "contains"
            )

            assertEquals(expected, actual)
        }
    }

    @Test
    fun minMaxItemsValidation() {
        val schema = SchemaLoader(JsonParser("""
            {
                "minItems": 3,
                "maxItems": 1
            }
        """.trimIndent())())()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            [1, 2]
        """.trimIndent())())!!

        assertEquals(JsonPointer("maxItems"), (actual.causes.filter { it.keyword == Keyword.MAX_ITEMS }.first() as MaxItemsValidationFailure).dynamicPath)
        assertEquals(JsonPointer("minItems"), (actual.causes.filter { it.keyword == Keyword.MIN_ITEMS }.first() as MinItemsValidationFailure).dynamicPath)
    }

    @Test
    fun `prefixItems dynamicPath`() {
        val schema = SchemaLoader("""
            { "prefixItems": [{"type": "string"}]}
        """.trimIndent())()

        val actual = Validator.forSchema(schema).validate("[2]") as PrefixItemsValidationFailure
        assertEquals("#/prefixItems", actual.dynamicPath.toString())
    }
}
