package com.github.erosb.jsonschema

enum class SpecificationVersion {
    DRAFT_2020_12
}

enum class Keyword(
    val value: String,
    internal val hasMapLikeSemantics: Boolean = false,
    val specificationVersion: SpecificationVersion = SpecificationVersion.DRAFT_2020_12
) {
    ID("\$id"),
    ANCHOR("\$anchor"),
    DYNAMIC_REF("\$dynamicRef"),
    DYNAMIC_ANCHOR("\$dynamicAnchor"),
    REF("\$ref"),
    DEFS("\$defs", true),
    MIN_LENGTH("minLength"),
    MAX_LENGTH("maxLength"),
    ALL_OF("allOf"),
    ADDITIONAL_PROPERTIES("additionalProperties"),
    PROPERTIES("properties", true),
    TITLE("title"),
    DESCRIPTION("description"),
    READ_ONLY("readOnly"),
    WRITE_ONLY("writeOnly"),
    DEPRECATED("deprecated"),
    DEFAULT("default"),
    ENUM("enum"),
    CONST("const"),
    FALSE("false"),
    TRUE("true"),
    TYPE("type")
}
