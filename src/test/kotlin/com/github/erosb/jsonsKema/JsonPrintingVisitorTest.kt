package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonPrintingVisitorTest {


    @Test
    fun `prints json`() {
        val str = """
            {
              "num": 1.234,
              "str": "hel\"l\\o",
              "bool": true,
              "arr": [
                1,
                2,
                null
              ]
            }
        """.trimIndent()
        val json = JsonParser(str.trimIndent())()

        val actual = json.toString()

        assertEquals(str, actual)
    }
}
