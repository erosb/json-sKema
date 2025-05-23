package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IfThenElseSchemaTest {

    val ifSchema = TrueSchema(UnknownSource)
    val thenSchema = FalseSchema(UnknownSource)
    val elseSchema = ConstSchema(JsonNumber(5), UnknownSource)

    @Test
    fun `subschemas contain all 3 clauses`() {
        val subject = IfThenElseSchema(ifSchema, thenSchema, elseSchema, UnknownSource)
        assertEquals(listOf(ifSchema, thenSchema, elseSchema), subject.subschemas())
    }

    @Test
    fun `subschemas contain only 2 clauses`() {
        val subject = IfThenElseSchema(ifSchema, thenSchema, null, UnknownSource)
        assertEquals(listOf(ifSchema, thenSchema), subject.subschemas())
    }

    @Test
    fun `dynamic path for then`() {
        val subject = IfThenElseSchema(ifSchema, thenSchema, elseSchema, UnknownSource)

        val actual = Validator.forSchema(subject).validate(JsonParser("null")()) as FalseValidationFailure

        assertEquals(JsonPointer("then", "false"), actual.dynamicPath.pointer)
    }


    @Test
    fun `dynamic path for else`() {
        val subject = IfThenElseSchema(FalseSchema(UnknownSource), thenSchema, elseSchema, UnknownSource)

        val actual = Validator.forSchema(subject).validate(JsonParser("4")()) as ConstValidationFailure

        assertEquals(JsonPointer("else", "const"), actual.dynamicPath.pointer)
    }
}
