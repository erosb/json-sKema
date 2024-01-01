package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MetaSchemaTest {

    @Spy
    lateinit var fallbackClient: SchemaClient

    @Test
    fun `draft2020-12 meta-schema loading`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "$ref": "https://json-schema.org/draft/2020-12/schema"
            }
        """)(), config = SchemaLoaderConfig(
            schemaClient = PrepopulatedSchemaClient(
                fallbackClient
            )
        )
        )()

        println(schema)
    }
}
