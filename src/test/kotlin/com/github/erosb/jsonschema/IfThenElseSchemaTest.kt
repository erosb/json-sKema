package com.github.erosb.jsonschema

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
    fun `subschemas contain onyl 2 clauses`() {
        val subject = IfThenElseSchema(ifSchema, thenSchema, null, UnknownSource)
        assertEquals(listOf(ifSchema, thenSchema), subject.subschemas())
    }
}
