package com.github.erosb.jsonsKema

import java.net.URI

class DynamicPath {

    private val path = mutableListOf<String>()

    lateinit var rootDocURI: URI

    fun <P> inSegmentPath(seg: String, cb: () -> P?): P? {
        path.add(seg)
        val rval = cb()
        path.removeLast()
        return rval
    }

    fun <P> inSegmentPath(seg: List<String>, cb: () -> P?): P? {
        path.addAll(seg)
        val rval = cb()
        val count = seg.size
        for (i in 1..count) path.removeLast()
        return rval
    }


    fun asPointer(): JsonPointer = JsonPointer(path.toList())

    fun setRootLoc(documentSource: URI) {
        rootDocURI = documentSource
    }


}
