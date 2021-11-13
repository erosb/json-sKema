package com.github.erosb.jsonschema

internal enum class SpecificationVersion {
    DRAFT_2020_12
}

internal enum class Keyword(val value: String,
                            val specificationVersion: SpecificationVersion = SpecificationVersion.DRAFT_2020_12) {
    ID("\$id"),
    ANCHOR("\$anchor"),
    DYNAMIC_REF("\$dynamicRef"),
    DYNAMIC_ANCHOR("\$dynamicAnchor"),
    REF("\$ref"),
    DEFS("\$defs"),
    MIN_LENGTH("minLength"),
    MAX_LENGTH("maxLength"),
    ALL_OF("allOf"),
    ADDITIONAL_PROPERTIES("additionalProperties"),
    PROPERTIES("properties"),
    TITLE("title"),
    DESCRIPTION("description"),
    READ_ONLY("readOnly"),
    WRITE_ONLY("writeOnly"),
    DEPRECATED("deprecated"),
    DEFAULT("default"),
    ENUM("enum"),
    CONST("const"),
}
