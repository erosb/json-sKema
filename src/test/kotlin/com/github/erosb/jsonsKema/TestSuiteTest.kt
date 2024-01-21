package com.github.erosb.jsonsKema

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.InputStream
import java.lang.IllegalStateException
import java.util.regex.Pattern
import java.util.stream.Stream

internal fun loadTests(input: InputStream): JsonArray {
    return JsonParser(input)().requireArray() as JsonArray
}

private fun <T : JsonValue> trimLeadingPointer(obj: T, pointerPrefixLength: Int): T {
    val pointerSegments = obj.location.pointer.segments
    if (pointerSegments.size < pointerPrefixLength) {
        throw IllegalStateException("$pointerSegments")
    }
    val trimmedLocation = obj.location.trimPointerSegments(pointerPrefixLength)
    return when (obj) {
        is JsonString -> obj.copy(location = trimmedLocation)
        is JsonBoolean -> obj.copy(location = trimmedLocation)
        is JsonNull -> obj.copy(location = trimmedLocation)
        is JsonNumber -> obj.copy(location = trimmedLocation)
        is JsonArray -> obj.copy(
            location = trimmedLocation,
            elements = obj.elements.map { trimLeadingPointer(it, pointerPrefixLength) },
        )

        is JsonObject -> obj.copy(
            location = trimmedLocation,
            properties = obj.properties.mapValues { trimLeadingPointer(it.value, pointerPrefixLength) },
        )

        else -> TODO()
    } as T
}

internal val SUPPORTED_FORMATS: List<String> = listOf(
    "/date.json",
    "/date-time.json",
    "/time.json",
    "/uri.json",
    "/email.json",
    "/ipv4.json",
    "/ipv6.json",
    "/uuid.json",
    "/duration.json"
)

internal fun loadParamsFromPackage(packageName: String, vararg fileFilters: String): List<Arguments> {
    val rval = mutableListOf<Arguments>()
    val refs = Reflections(
        packageName,
        ResourcesScanner(),
    )
    val excludedFiles = fileFilters
        .filter { it.startsWith("!") }
        .map { it.substring(1) }
    val includedFiles = fileFilters.filter { !it.startsWith("!") }.toTypedArray()
    val paths: Set<String> = refs.getResources(Pattern.compile(".*\\.json"))
    for (path in paths) {
        if (path.indexOf("/remotes/") > -1) {
            continue
        }
        if (path.indexOf("/optional/") > -1) {
            if (path.indexOf("/format/") == -1
                || SUPPORTED_FORMATS.none { path.endsWith(it) }) {
                continue
            }
        }
        val fileName = path.substring(path.lastIndexOf('/') + 1)
        if ((includedFiles.isNotEmpty() && !includedFiles.contains(fileName)) || excludedFiles.contains(fileName)) {
            continue
        }
        val arr: JsonArray = loadTests(TestSuiteTest::class.java.getResourceAsStream("/$path"))
        for (i in 0 until arr.length()) {
            val schemaTest: JsonObject = arr[i].requireObject() as JsonObject
            val testcaseInputs: IJsonArray<*> = schemaTest["tests"]!!.requireArray()
            for (j in 0 until testcaseInputs.length()) {
                val input: JsonObject = testcaseInputs[j].requireObject() as JsonObject
                val testcase = TestCase(input, schemaTest, fileName, path.contains("optional/format/"))
                rval.add(Arguments.of(testcase, testcase.schemaDescription))
            }
        }
    }
    return rval
}

class TestCase(input: JsonObject, schemaTest: JsonObject, fileName: String, val isFormatTest: Boolean) {
    val schemaDescription = "[" + fileName + ":" + input.location.lineNumber + "]/" + schemaTest["description"]!!.requireString().value
    val schemaJson: IJsonValue = trimLeadingPointer(schemaTest["schema"]!!, 2)
    val inputDescription = schemaDescription + "/" + input["description"]!!.requireString().value
    val expectedToBeValid = input["valid"]!!.requireBoolean().value
    val inputData: IJsonValue = input["data"]!!

    lateinit var schema: Schema

    fun loadSchema() {
        schema = SchemaLoader(schemaJson)()
    }

    fun run() {
        val validator = Validator.create(schema, ValidatorConfig(
            validateFormat = if (isFormatTest)
                FormatValidationPolicy.ALWAYS
            else
                FormatValidationPolicy.NEVER
        ))
        val failure = validator.validate(inputData)
        val isValid = failure === null
        if (isValid != expectedToBeValid) {
            if (!isValid) {
                println(failure!!.toJSON())
            }
            fail("$inputDescription : isValid: $isValid, expectedToBeValid: $expectedToBeValid")
        }
    }

    override fun toString(): String = inputDescription
}

@Tag("acceptance")
class TestSuiteTest {
    companion object {
        @JvmStatic
        fun params(): Stream<Arguments> = loadParamsFromPackage(
            "test-suite.tests.draft2020-12",
//            "unevaluatedProperties.json",
//            "dynamicRef.json",
//            "anchor.json",
//            "ref.json",
//            "id.json",
//            "refRemote.json",
        ).stream()

        private val server = JettyWrapper("/test-suite/remotes")

        @JvmStatic
        @BeforeAll
        fun startJetty() = server.start()

        @JvmStatic
        @AfterAll
        fun stopJetty() = server.stop()
    }

    @ParameterizedTest
    @MethodSource("params")
    fun run(tc: TestCase) {
        tc.loadSchema()
        tc.run()
    }
}
