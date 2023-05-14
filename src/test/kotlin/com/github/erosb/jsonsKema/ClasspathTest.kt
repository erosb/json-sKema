package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test

class ClasspathTest {

    @Test
    fun testLoadFromURL() {
        val loader = SchemaLoader.forURL("classpath://classpath-base/subdir/root.json")
        val sch = loader.load()
    }
}
