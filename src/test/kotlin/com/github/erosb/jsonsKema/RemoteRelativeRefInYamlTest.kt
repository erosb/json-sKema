package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URL

class RemoteRelativeRefInYamlTest {

    @Test
    fun testWithForURL() {
        val schema = SchemaLoader.forURL("classpath://remote-relative-ref-in-yaml/pets-api.yaml")()

        Validator.forSchema(schema).validate(JsonParser("""
               {
                    "name": "Todor"
                }
        """.trimIndent())())
    }


    @Test
    fun testWithForSchema() {
        val schema = SchemaLoader("""
            $ref: "#/components/schemas/CreatePetRequest"
            components:
              schemas:
                CreatePetRequest:
                  type: object
                  additionalProperties: false
                  required:
                    - id
                    - owner
                  properties:
                    name:
                      $ref: "#/components/schemas/Name"
                    owner:
                      $ref: "./common-types.yaml#/UserIdentifier"
                Name:
                  type: string
                  minLength: 1

        """.trimIndent(), URI("classpath://remote-relative-ref-in-yaml/pets-api.yaml"))()


        Validator.forSchema(schema).validate(JsonParser("""
               {
                    "name": "Todor"
                }
        """.trimIndent())())
    }
}
