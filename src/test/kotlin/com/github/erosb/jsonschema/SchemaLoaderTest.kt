package com.github.erosb.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows

class SchemaLoaderTest {

    @Test
    fun `loads true schema`() {
        val underTest = createSchemaLoaderForString("true")
        assertThat(underTest()).isEqualTo(TrueSchema(SourceLocation(1, 1, pointer())))
    }

    @Test
    fun `loads false schema`() {
        val underTest = createSchemaLoaderForString("false");
        assertThat(underTest()).isEqualTo(FalseSchema(SourceLocation(1, 1, pointer())))
    }

    @Test
    fun `loads minLength schema`() {
        val underTest = createSchemaLoaderForString("""
            { "minLength": 20}
        """.trimIndent())
        assertThat(underTest()).isEqualTo(CompositeSchema(setOf(
                MinLengthSchema(20, SourceLocation(1, 3, pointer("minLength")))
        ),
                SourceLocation(1, 1, pointer())
        ))
    }

    @Test
    fun `invalid minLength fractional`() {
        val exc = assertThrows(JsonTypingException::class.java) {
            createSchemaLoaderForString("""
                    { "minLength": 20.0}
                """.trimIndent())()
        }
        assertThat(exc).isEqualTo(JsonTypingException("integer", "number", SourceLocation(1, 16, pointer("minLength"))))
    }
}
