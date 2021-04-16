package com.github.erosb.jsonschema

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.InputStream
import java.util.regex.Pattern
import java.util.stream.Stream

internal fun loadTests(input: InputStream): IJsonArray<*> {
    return JsonParser(input)().requireArray()
}

internal fun loadParamsFromPackage(packageName: String): List<Arguments> {
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
        val arr: IJsonArray<*> = loadTests(TestSuiteTest::class.java.getResourceAsStream("/$path"))
        for (i in 0 until arr.length()) {
            val schemaTest: IJsonObject<*,*> = arr[i].requireObject()
            val testcaseInputs: IJsonArray<*> = schemaTest["tests"]!!.requireArray()
            for (j in 0 until testcaseInputs.length()) {
                val input: IJsonObject<*, *> = testcaseInputs[j].requireObject()
                val testcase = TestCase(input, schemaTest, fileName)
                rval.add(Arguments.of(testcase, testcase.schemaDescription))
            }
        }
    }
    return rval
}

class TestCase(input: IJsonObject<*,*>, schemaTest: IJsonObject<*,*>, fileName: String) {
    val schemaDescription = "[" + fileName + "]/" + schemaTest["description"]!!.requireString().value
    val schemaJson: IJsonValue = schemaTest["schema"]!!
    val inputDescription = "[" + fileName + "]/" + input["description"]!!.requireString().value
    val expectedToBeValid = input["valid"]!!.requireBoolean().value
    val inputData: IJsonValue = input["data"]!!
    
    lateinit var schema: Schema;
    
    fun loadSchema() {
        schema = SchemaLoader(schemaJson)()
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
    }
}
