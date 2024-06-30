package com.github.erosb.jsonsKema_fluent

import com.github.erosb.jsonsKema.*
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class SchemaBuilderTest {

    @Test
    fun test() {
        val schema = SchemaBuilder.typeString()
            .minLength(2)
            .maxLength(3)
            .build()

        val failure = Validator.forSchema(schema).validate(JsonParser("\"\"")())!!

        assertThat(failure.message).isEqualTo("actual string length 0 is lower than minLength 2")
        assertThat(failure.schema.location.lineNumber).isEqualTo(14)
        assertThat(failure.schema.location.documentSource).isEqualTo(URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"))
        assertThat(failure.schema.location.pointer).isEqualTo(JsonPointer("minLength"))
        SourceLocation(
            lineNumber = 13,
            position = 0,
            documentSource = URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"),
            pointer = JsonPointer(listOf("minLength"))
        )
    }

    @Test
    fun `object properties`() {
        val schema = SchemaBuilder.typeObject()
            .property("propA", SchemaBuilder.typeString()
                .minLength(4)
            )
            .build()
//            .property("propA") { it.typeString().build() }

        val failure = Validator.forSchema(schema).validate(JsonParser("""
            {
              "propA": "bad"
            }
        """.trimIndent())())!!

        assertThat(failure.message).isEqualTo("actual string length 3 is lower than minLength 4")
        assertThat(failure.schema.location.lineNumber).isEqualTo(36)
        assertThat(failure.schema.location.documentSource).isEqualTo(URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"))
        assertThat(failure.schema.location.pointer).isEqualTo(JsonPointer("properties", "minLength"))
    }
}
