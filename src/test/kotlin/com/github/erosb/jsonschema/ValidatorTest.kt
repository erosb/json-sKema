package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ValidatorTest {

    @Test
    fun `ValidationFailure props`() {
        val schema = CompositeSchema(
            subschemas = setOf(
                MinLengthSchema(5, SourceLocation(5, 5, JsonPointer(listOf("parent", "minLength"))))
            ), UnknownSource
        )
        val instance = JsonParser("  \"hey\"")()
        val actual = Validator.forSchema(schema).validate(instance)!!

        println(actual)
        assertEquals("Line 1, character 3: expected minLength: 5, actual: 3", actual.toString())
        assertSame(schema.subschemas.stream().findFirst().get(), actual.schema)
        assertSame(instance, actual.instance)
        assertSame(Keyword.MIN_LENGTH, actual.keyword)
    }

    @Test
    fun `multiple ValidationFailures`() {
        val minLengthSchema = MinLengthSchema(5, SourceLocation(5, 5, JsonPointer(listOf("parent", "minLength"))))
        val maxLengthSchema = MaxLengthSchema(3, UnknownSource)
        val schema = CompositeSchema(
            subschemas = setOf(
                minLengthSchema,
                maxLengthSchema
            ), location = UnknownSource
        )
        val instance = JsonParser("  \"heyy\"")()
        val actual = Validator.forSchema(schema).validate(instance)!!
        assertEquals("Line 1, character 3: multiple validation failures", actual.toString())
        assertEquals(
            setOf(
                ValidationFailure("expected minLength: 5, actual: 4", minLengthSchema, instance, Keyword.MIN_LENGTH),
                ValidationFailure("expected maxLength: 3, actual: 4", maxLengthSchema, instance, Keyword.MAX_LENGTH)
            ), actual.causes
        )
    }
}
