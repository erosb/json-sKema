package com.github.erosb.jsonsKema

internal class JsonPrintingVisitor(private val indentation: String = "  ") : JsonVisitor<String> {

    private var indentLevel: Int = 0

    override fun visitString(str: IJsonString) = '"' + escape(str.value) + '"'

    private fun escape(str: String) = str.replace("\\", "\\\\").replace("\"", "\\\"")

    override fun visitBoolean(bool: IJsonBoolean) = bool.value.toString()

    override fun visitNumber(num: IJsonNumber) = num.value.toString()

    override fun visitNull(nil: IJsonNull) = "null"

    override fun visitArray(arr: IJsonArray<*>): String {
        val baseIndent = indentation.repeat(indentLevel)
        val nestedIndent = baseIndent + indentation
        indentLevel++
        val rval = arr.elements
            .map { el -> el.accept(this) }
            .joinToString(
                prefix = "[\n$nestedIndent",
                separator = ",\n$nestedIndent",
                postfix = "\n$baseIndent]"
            )
        indentLevel--
        return rval
    }

    override fun visitObject(obj: IJsonObject<*, *>): String? {
        val baseIndent = indentation.repeat(indentLevel)
        val nestedIndent = baseIndent + indentation
        indentLevel++
        val rval = obj.properties
            .map { entry -> "${entry.key.accept(this)}: ${entry.value.accept(this)}" }
            .joinToString(
                prefix = "{\n$nestedIndent",
                separator = ",\n$nestedIndent",
                postfix = "\n$baseIndent}"
            )
        indentLevel--
        return rval
    }
}
