package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertiesValidationTest {

    @Test
    fun `multiple type failures under properties`() {
        val actual = Validator.forSchema(
            SchemaLoader(
                JsonParser(
                    """
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
                    },
                    "realObjProp": {
                        "type": "object",
                        "properties": {
                            "nestedProp": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        """
                )()
            )()
        ).validate(
            JsonParser(
                """
            {
                "arrProp": true,
                "objProp": null,
                "strProp": 20,
                "otherProp": "x",
                "realObjProp": {
                    "nestedProp": 2
                }
            }
        """
            )()
        )!!.toJSON()

        val expected = JsonParser(
            """
            {
                "instanceRef": "#"
                "schemaRef": "#",
                "message": "multiple validation failures",
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
                    },
                    {
                        "instanceRef": "#/strProp",
                        "schemaRef": "#/properties/strProp/type",
                        "message": "expected type: string, actual: integer",
                        "keyword": "type"
                    },
                    {
                      "instanceRef": "#/realObjProp/nestedProp",
                      "schemaRef": "#/properties/realObjProp/properties/nestedProp/type",
                      "message": "expected type: string, actual: integer",
                      "keyword": "type"
                    }
                ]
            }
        """
        )()

        assertEquals(expected, actual)
    }
}
