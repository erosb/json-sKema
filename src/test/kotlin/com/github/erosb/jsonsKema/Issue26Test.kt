package com.github.erosb.jsonsKema

import com.github.erosb.jsonsKema.Validator.Companion.forSchema
import org.junit.jupiter.api.Test

class Issue26Test {

    @Test
    fun testReferences() {
        val doc = """{
  "customerName": "acme",
  "acquireDate": "2020-12-12"
}"""
        val jsonValue = JsonParser(doc).parse()
        val userSchema = """{
  "type": "object",
  "properties": {
    "age": {
      "type": "integer",
      "minimum": 0
    }
  },
  "additionalProperties": false,
  "required": [
    "age"
  ]
}
"""
        val schemaJson = JsonParser(userSchema).parse()
        val loadedSchema = SchemaLoader(schemaJson).load()
        val validator = forSchema(loadedSchema)
        println(validator.validate(jsonValue)?.toJSON())
    }
}
