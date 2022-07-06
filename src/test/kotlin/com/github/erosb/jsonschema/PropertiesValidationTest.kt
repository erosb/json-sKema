package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertiesValidationTest {

    @Test
    fun `multiple type failures under properties`() {
        val actual = Validator.forSchema(SchemaLoader(JsonParser("""
            {
                "type": "object",
                "properties": {
                    "arrProp": {
                        "type": "array"
                    }          ,
                    "objProp": {
                        "type": "object"
                    },
                    "strProp": {
                        "type": "string"
                    }
                }
            }
        """)())()).validate(JsonParser("""
            {
                "arrProp": true,
                "objProp": null,
                "strProp": 20,
                "otherProp": "x"
            }
        """)())!!.toJSON()

        val expected = JsonParser("""
            {
                "instanceRef": "#"
                "schemaRef": "#",
                "message": "multiple schema violations found",
                "causes": [
                    {
                        "message": "expected type: array, actual: boolean",
                        "schemaRef": "#/properties/arrProp/type",
                        "instanceRef": "#/arrProp",
                        "keyword": "type"
                    },
                    {
                        "message": "expected type: object, actual: null",
                        "schemaRef": "#/properties/objProp/type",
                        "instanceRef": "#/objProp",
                        "keyword": "type"
                    }
                ]
            }
        """)()

        assertEquals(expected, actual)
    }
}
