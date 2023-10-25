package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DynamicRefTest {

    @Test
    fun testArrayGenerics() {
        val schema = createSchemaLoaderForString(
            """
            {
                "type": "object",
                "properties": {
                    "intList": {
                        "$ref": "#/$defs/List",
                        "$defs": {
                            "elemType": {
                                "$dynamicAnchor": "elemType",
                                "type": "integer"
                            }
                        }
                    }
                }
                "$defs": {
                    "List": {
                        "type": "array",
                        "items": {
                            "$dynamicRef": "#elemType"
                        }
                    }
                }
            }
            """.trimIndent()
        )()
        val actual = Validator.forSchema(schema).validate(
            JsonParser(
                """
            {
                "intList": [false]
            }
        """
            )()
        )

        println(actual)
        assertThat(actual).isNotNull()
    }
}
