package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicRefResolutionTest {
    
    @Test
    fun `$dynamicRef looks up $dynamicAnchor`() {
        val root: CompositeSchema = createSchemaLoaderForString("""
            {
                "properties": {
                    "a": {
                        "title": "properties/a"
                        "type": "object",
                        "$dynamicAnchor": "anchorName",
                        "$ref": "#/$defs/Referred"
                    }
                }
                "$defs": {
                    "Referred": {
                        "properties": {
                            "anchored": {
                                "$dynamicRef": "anchorName"
                            }
                        }
                    }
                }
            }
        """)() as CompositeSchema
        println(root)
        val actualTitle: String = root.accept(TraversingVisitor("properties", "a", "$ref", "properties", "anchored",
            // "$dynamicRef"
            "title"
        ))!!
        assertEquals("properties/a", actualTitle)
    }
}
