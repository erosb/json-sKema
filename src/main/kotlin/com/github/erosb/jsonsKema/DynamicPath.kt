package com.github.erosb.jsonsKema

import java.net.URI

class DynamicPath() {

    constructor(rootDocumentSource: URI, path: List<String>): this() {
        this.rootDocumentSource = rootDocumentSource
        this.path.addAll(path)
    }

    private val path = mutableListOf<String>()

    internal lateinit var rootDocumentSource: URI

    fun <P> inSegmentPath(
        seg: String,
        cb: () -> P?,
    ): P? {
        path.add(seg)
        val rval = cb()
        path.removeLast()
        return rval
    }

    fun <P> inSegmentPath(
        seg: List<String>,
        cb: () -> P?,
    ): P? {
        path.addAll(seg)
        val rval = cb()
        val count = seg.size
        for (i in 1..count) path.removeLast()
        return rval
    }

    val pointer: JsonPointer
        get() = JsonPointer(path.toList())

    fun copy() = DynamicPath(rootDocumentSource, path.toMutableList())

    operator fun plus(keyword: Keyword): DynamicPath = plus(keyword.value)

    operator fun plus(keyword: String): DynamicPath = DynamicPath(rootDocumentSource, path + keyword)

    override fun toString(): String = rootDocumentSource.toString() + pointer.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DynamicPath

        if (path != other.path) return false
        if (rootDocumentSource != other.rootDocumentSource) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + rootDocumentSource.hashCode()
        return result
    }


}
