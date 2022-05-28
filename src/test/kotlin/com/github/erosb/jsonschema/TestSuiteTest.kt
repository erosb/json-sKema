package com.github.erosb.jsonschema

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
            elements = obj.elements.map { trimLeadingPointer(it, pointerPrefixLength) }
        )
        is JsonObject -> obj.copy(
            location = trimmedLocation,
            properties = obj.properties.mapValues { trimLeadingPointer(it.value, pointerPrefixLength) }
        )
        else -> TODO()
    } as T
}

internal fun loadParamsFromPackage(packageName: String, vararg includedFiles: String): List<Arguments> {
    val rval = mutableListOf<Arguments>()
    val refs = Reflections(
        packageName,
        ResourcesScanner()
    )
    val paths: Set<String> = refs.getResources(Pattern.compile(".*\\.json"))
    for (path in paths) {
        if (path.indexOf("/optional/") > -1 || path.indexOf("/remotes/") > -1) {
            continue
        }
        val fileName = path.substring(path.lastIndexOf('/') + 1)
        if (includedFiles.isNotEmpty() && !includedFiles.contains(fileName)) {
            continue;
        }
        val arr: JsonArray = loadTests(TestSuiteTest::class.java.getResourceAsStream("/$path"))
        for (i in 0 until arr.length()) {
            val schemaTest: JsonObject = arr[i].requireObject() as JsonObject
            val testcaseInputs: IJsonArray<*> = schemaTest["tests"]!!.requireArray()
            for (j in 0 until testcaseInputs.length()) {
                val input: JsonObject = testcaseInputs[j].requireObject() as JsonObject
                val testcase = TestCase(input, schemaTest, fileName)
                rval.add(Arguments.of(testcase, testcase.schemaDescription))
            }
        }
    }
    return rval
}

class TestCase(input: JsonObject, schemaTest: JsonObject, fileName: String) {
    val schemaDescription = "[" + fileName + "]/" + schemaTest["description"]!!.requireString().value
    val schemaJson: IJsonValue = trimLeadingPointer(schemaTest["schema"]!!, 2)
    val inputDescription = "[" + fileName + "]/" + input["description"]!!.requireString().value
    val expectedToBeValid = input["valid"]!!.requireBoolean().value
    val inputData: IJsonValue = input["data"]!!

    lateinit var schema: Schema;

    fun loadSchema() {
        schema = SchemaLoader(schemaJson)()
    }

    fun run() {
        val validator = Validator.forSchema(schema)
        val failure = validator.validate(inputData)
        val isValid = failure === null
        if (isValid != expectedToBeValid) {
            fail("isValid: $isValid, expectedToBeValid: $expectedToBeValid")
        }
    }

    override fun toString(): String = inputDescription
}

class TestSuiteTest {
    companion object {
        @JvmStatic
        fun params(): Stream<Arguments> = loadParamsFromPackage("com.github.erosb.jsonschema.tests.draft202012").stream()

        private val server = JettyWrapper("/com/github/erosb/jsonschema/tests/remotes")

        @JvmStatic
        @BeforeAll
        fun startJetty() = server.start()


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
