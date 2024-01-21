package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

class VocabularyHandlingTest {

    @Test
    fun `format validation is disabled by default`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema"
                "format": "email"
            }
        """)()
        )() as CompositeSchema

        val actual = Validator.forSchema(schema).validate(JsonParser("""
           "not-an-email" 
        """)())

        assertNull(actual)
    }

    @Test
    fun `format validation is explicitly enabled`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema"
                "format": "email"
            }
        """)()
        )() as CompositeSchema

        val actual = Validator.create(schema, ValidatorConfig(validateFormat = FormatValidationPolicy.ALWAYS)).validate(JsonParser("""
           "not-an-email" 
        """)())

        assertNotNull(actual)
    }

    @Test
    fun `format validation is enabled if meta-schema is missing`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "format": "email"
            }
        """)()
        )() as CompositeSchema

        val actual = Validator.forSchema(schema).validate(JsonParser("""
           "not-an-email" 
        """)())

        assertNotNull(actual)
    }

    @Test
    fun `format validation is enabled by meta-schema`() {
        val schema =
            SchemaLoader(
                JsonParser("""
                    {"$schema": "http://my-meta-schema", "format": "email"}
                """)(), config =
                    createDefaultConfig(
                        mapOf(
                            URI("http://my-meta-schema") to
                                """{"$vocabulary": {
                                        "https://json-schema.org/draft/2020-12/vocab/format-assertion": true,   
                                   }}""".trimIndent()
                        )
                    )
            )() as CompositeSchema

        val actual =
            Validator.forSchema(schema).validate(
                JsonParser(
                    """
                        "not-an-email" 
                    """)()
            )

        assertNotNull(actual)
    }

    @Test
    fun `vocab loading only true valued vocabs are loaded`() {
        val schema =
            SchemaLoader(
                JsonParser("""
                    {"$schema": "http://my-meta-schema", "format": "email"}
                """)(), config =
                createDefaultConfig(
                    mapOf(
                        URI("http://my-meta-schema") to
                                """{"$vocabulary": {
                                        "https://json-schema.org/draft/2020-12/vocab/format-annotation": true,
                                        "https://json-schema.org/draft/2020-12/vocab/format-assertion": false   
                                   }}""".trimIndent()
                    )
                )
            )() as CompositeSchema

        val actual =
            Validator.forSchema(schema).validate(
                JsonParser(
                    """
                        "not-an-email" 
                    """)()
            )

        assertNull(actual)
    }
}
