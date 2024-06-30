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
        SourceLocation(
            lineNumber = 13,
            position = 0,
            documentSource = URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"),
            pointer = JsonPointer(listOf("minLength"))
        )
    }
}
