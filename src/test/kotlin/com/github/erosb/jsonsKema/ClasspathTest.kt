package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClasspathTest {

    @Test
    fun testLoadFromURL() {
        val loader = SchemaLoader.forURL("classpath://classpath-base/subdir/root.json")
        val sch = loader.load()
        val actual = Validator.forSchema(sch).validate(JsonParser("""
            {
                "prop1": "bogus",
                "prop2": "values"
            }
        """.trimIndent())())

        assertEquals(2, actual!!.causes.size)
    }

    @Test
    fun `an other test with subschemas referring to each other`() {
        val loader = SchemaLoader.forURL("classpath://classpath-base/subdir/Project.json")
        val sch = loader.load()
        val actual = Validator.forSchema(sch).validate(JsonParser("""
            {
                "prop1": "bogus",
                "prop2": "values"
            }
        """.trimIndent())())
    }
}
