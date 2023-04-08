package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException

class JsonValueTest {

    @Test
    fun trimPointerSegments_success() {
        val location = SourceLocation(1, 2, JsonPointer(listOf("seg1", "seg2", "seg3")))
        val actual = location.trimPointerSegments(2)
        assertEquals(SourceLocation(1, 2, JsonPointer(listOf("seg3"))), actual)
    }

    @Test
    fun trimPointerSegments_emptySegmentRemains() {
        val location = SourceLocation(1, 2, JsonPointer(listOf("seg1", "seg2")))
        val actual = location.trimPointerSegments(2)
        assertEquals(SourceLocation(1, 2, JsonPointer(emptyList())), actual)
    }

    @Test
    fun trimPointerSegments_throwsException_ifNotEnoughSegments() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            SourceLocation(1, 2, JsonPointer(listOf("seg1", "seg2"))).trimPointerSegments(3)
        }
        assertEquals("can not remove 3 segment from pointer #/seg1/seg2", thrown.message)
    }
}
