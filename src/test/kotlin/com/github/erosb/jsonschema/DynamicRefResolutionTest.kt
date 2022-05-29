package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DynamicRefResolutionTest {
    
    @Test
    fun `$dynamicRef looks up $dynamicAnchor`() {
        val root: CompositeSchema = createSchemaLoaderForString("""
            {
                "$id": "https://example.com/root",
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
                                "$dynamicRef": "#anchorName"
                            }
                        }
                    }
                }
            }
        """)() as CompositeSchema
        val actualTitle: String = root.accept(TraversingSchemaVisitor("properties", "a", "$ref", "properties", "anchored", "title"))!!
        assertEquals("properties/a", actualTitle)
    }

    @Test
    fun `multiple dynamic paths`() {
        val root: CompositeSchema = createSchemaLoaderForString("""
            {
                "$id": "https://example.com/root",
                "properties": {
                    "a": {
                        "title": "properties/a"
                        "type": "object",
                        "$dynamicAnchor": "anchorName",
                        "$ref": "#/$defs/Referred"
                    },
                    "b": {
                        "title": "properties/b"
                        "$dynamicAnchor": "anchorName",
                        "$ref": "#/$defs/Referred"
                    }
                }
                "$defs": {
                    "Referred": {
                        "properties": {
                            "anchored": {
                                "$dynamicRef": "#anchorName"
                            }
                        }
                    }
                }
            }
        """)() as CompositeSchema
        val aTitle: String = root.accept(TraversingSchemaVisitor("properties", "a", "$ref", "properties", "anchored", "title"))!!
        assertEquals("properties/a", aTitle)
        val bTitle: String = root.accept(TraversingSchemaVisitor("properties", "b", "$ref", "properties", "anchored", "title"))!!
        assertEquals("properties/b", bTitle)
    }

    @Test
    fun `first anchor in the dynamic scope wins`() {
        val root: CompositeSchema = createSchemaLoaderForString("""
            {
                "$id": "https://example.com/root",
                "title": "outer title",
                "$dynamicAnchor": "anchorName",
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
                                "$dynamicRef": "#anchorName"
                            }
                        }
                    }
                }
            }
        """)() as CompositeSchema
        val aTitle: String = root.accept(TraversingSchemaVisitor("properties", "a", "$ref", "properties", "anchored", "title"))!!
        assertEquals("outer title", aTitle)
    }

}
