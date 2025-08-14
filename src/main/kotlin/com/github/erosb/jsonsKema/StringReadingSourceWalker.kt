package com.github.erosb.jsonsKema

import java.net.URI

internal class StringReadingSourceWalker(
    private val input: CharArray,
    documentSource: URI
) : SourceWalker(documentSource) {
    private val inputSize = input.size
    private var mark = 0
    private var pos = 0
    override fun readCharInto(): Int {
        if (reachedEOF()) return -1
        buf[0] = input[pos++]
//        ++position
        return 1
    }

    override fun forward() {
        ++pos
        ++position
    }

    override fun curr(): Char {
        if (reachedEOF()) throw JsonParseException("Unexpected EOF", location)
        return input[pos]
    }

    override fun currInt(): Int {
        if (reachedEOF()) return -1
        return input[pos].code
    }

    override fun mark() {
        mark = pos
    }

    override fun reset() {
//        position -= (pos - mark)
        pos = mark
    }

    override fun reachedEOF(): Boolean = pos == inputSize

    override fun hasAtLeastNRemainingChars(n: Int): Boolean {
        return inputSize - pos > n
    }

    override fun unsafeNext(): Char = input[pos + 1]


}
