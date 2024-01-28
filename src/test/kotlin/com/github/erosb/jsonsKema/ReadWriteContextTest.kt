package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadWriteContextTest {

    @Test
    fun `validation in write context fails on readonly prop`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "readOnly": true
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.WRITE
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        println(actual?.toJSON())
        assertEquals("read-only property \"id\" should not be present in write context", actual!!.message)
    }


    @Test
    fun `validation in read context succeeds on readonly prop`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "readOnly": true
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.READ
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        assertNull(actual)
    }

    @Test
    fun `validation in read context fails on writeonly prop`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "writeOnly": true
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.READ
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        println(actual?.toJSON())
        assertEquals("write-only property \"id\" should not be present in read context", actual!!.message)
    }

    @Test
    fun `validation in write context succeeds on writeonly prop`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "writeOnly": true
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.WRITE
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        assertNull(actual)
    }

    @Test
    fun `readOnly false does not impact validation`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "readOnly": false
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.WRITE
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        assertNull(actual)
    }

    @Test
    fun `writeOnly false does not impact validation`() {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "writeOnly": false
                    }
                }
            }
        """)())()

        val validator = Validator.create(schema, ValidatorConfig(
            readWriteContext = ReadWriteContext.READ
        ))

        val actual = validator.validate(JsonParser("""
           {
            "id": 3
           } 
        """)())

        assertNull(actual)
    }

    @Test
    fun `readOnly and writeOnly does not impact NONE context`()  {
        val schema = SchemaLoader(JsonParser("""
            {
                "properties": {
                    "id": {
                        "type": "integer",
                        "writeOnly": true
                    },
                    "name": {
                        "readOnly": true
                    }
                }
            }
        """)())()

        val validator = Validator.forSchema(schema)

        val actual = validator.validate(JsonParser("""
           {
            "id": 3, "name": "asd"
           } 
        """)())

        assertNull(actual)
    }
}
