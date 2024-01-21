package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension

val schema = Keyword.SCHEMA.value
val vocabulary = Keyword.VOCABULARY.value


@ExtendWith(MockitoExtension::class)
class MetaSchemaTest {

    @Spy
    lateinit var fallbackClient: SchemaClient

    @Test
    fun `draft2020-12 meta-schema loading`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "$ref": "https://json-schema.org/draft/2020-12/schema",
                "$schema": "https://json-schema.org/draft/2020-12/schema"
            }
        """)(), config = SchemaLoaderConfig(
            schemaClient = PrepopulatedSchemaClient(
                fallbackClient
            )
        )
        )() as CompositeSchema

        assertThat(schema.vocabulary).isEqualTo(listOf(
            "https://json-schema.org/draft/2020-12/vocab/core",
            "https://json-schema.org/draft/2020-12/vocab/applicator",
            "https://json-schema.org/draft/2020-12/vocab/unevaluated",
            "https://json-schema.org/draft/2020-12/vocab/validation",
            "https://json-schema.org/draft/2020-12/vocab/meta-data",
            "https://json-schema.org/draft/2020-12/vocab/format-annotation",
            "https://json-schema.org/draft/2020-12/vocab/content"
        ))
    }
}
