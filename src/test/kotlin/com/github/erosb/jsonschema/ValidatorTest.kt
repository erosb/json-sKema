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
            ),
            UnknownSource
        )
        val instance = JsonParser("  \"hey\"")()
        val actual = Validator.forSchema(schema).validate(instance)!!

        assertEquals("Line 1, character 3: actual string length 3 is lower than minLength 5", actual.toString())
        assertSame(schema.subschemas.stream().findFirst().get(), actual.schema)
        assertSame(instance, actual.instance)
        assertSame(Keyword.MIN_LENGTH, actual.keyword)
    }

    @Test
    fun `multiple ValidationFailures`() {
        val minLengthSchema = MinLengthSchema(5, SourceLocation(5, 5, JsonPointer(listOf())))
        val maxLengthSchema = MaxLengthSchema(3, UnknownSource)
        val falseSchema = FalseSchema(UnknownSource)
        val schema = CompositeSchema(
            subschemas = setOf(
                minLengthSchema,
                maxLengthSchema,
                falseSchema
            ),
            location = UnknownSource
        )
        val instance = JsonParser("  \"heyy\"")()
        val actual = Validator.forSchema(schema).validate(instance)!!
        assertEquals("Line 1, character 3: multiple validation failures", actual.toString())

        assertEquals(
            JsonParser(
                """
            {
                "instanceRef": "#",
                "schemaRef": "#",
                "message": "multiple validation failures",
                "causes": [
                    {
                        "instanceRef": "#",
                        "schemaRef": "#",
                        "message": "actual string length 4 is lower than minLength 5",
                        "keyword": "minLength"
                    },
                    {
                        "instanceRef": "#",
                        "schemaRef": "#",
                        "message": "actual string length 4 exceeds maxLength 3",
                        "keyword": "maxLength"
                    },
                    {
                        "instanceRef": "#",
                        "schemaRef": "#",
                        "message": "false schema always fails",
                        "keyword": "false"
                    }
                ]
            }
        """
            )(),
            actual.toJSON()
        )
    }
}
