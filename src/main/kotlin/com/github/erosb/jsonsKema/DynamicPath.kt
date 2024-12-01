package com.github.erosb.jsonsKema

class DynamicPath {

    private val path = mutableListOf<String>()

    fun <P> inSegmentPath(seg: String, cb: () -> P?): P? {
        path.add(seg)
        val rval = cb()
        path.removeLast()
        return rval
    }

    fun asPointer(): JsonPointer = JsonPointer(path)


}
