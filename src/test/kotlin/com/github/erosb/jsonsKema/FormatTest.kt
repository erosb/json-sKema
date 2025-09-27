package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class FormatTest {

    val DATE_TIME_SCHEMA = FormatSchema("date-time", UnknownSource)

    @Test
    fun invalidDate() {
        val schema = FormatSchema("date", UnknownSource)
        val instance = JsonString("asdasd")
        val actual = Validator.create(schema, ValidatorConfig(validateFormat = FormatValidationPolicy.ALWAYS))
            .validate(instance)!!

        assertThat(actual).isEqualTo(FormatValidationFailure(schema, instance))
        assertThat(actual.message).isEqualTo("instance does not match format 'date'")
    }

    @Test
    fun date_time_invalidDayOfMonth() {
        val instance = JsonString("1990-02-31T15:59:59.123-08:00")

        val actual = Validator.create(DATE_TIME_SCHEMA, ValidatorConfig(validateFormat = FormatValidationPolicy.ALWAYS))
            .validate(instance)!!

        assertThat(actual).isNotNull()
    }

    @Test
    fun date_time_extendedYear() {
        val instance = JsonString("+11990-03-31T15:59:59.123-08:00")

        val actual = Validator.create(DATE_TIME_SCHEMA, ValidatorConfig(validateFormat = FormatValidationPolicy.ALWAYS))
            .validate(instance)

        assertThat(actual).isNotNull()
    }

    @Test
    @Disabled
    fun `date-time valid leap second at UTC`() {
        val instance = JsonString("1990-02-31T15:59:59.123-08:00")

        val actual = Validator.create(DATE_TIME_SCHEMA, ValidatorConfig(validateFormat =  FormatValidationPolicy.ALWAYS))
            .validate(instance)

        assertThat(actual).isNull()
    }

    @Test
    fun uuid_shiftedDashes() {
        val instance = JsonString("2eb8aa0-8aa98-11e-ab4aa7-3b441d16380")

        val uuidSchema = FormatSchema("uuid", UnknownSource)

        val actual = Validator.create(uuidSchema, ValidatorConfig(validateFormat = FormatValidationPolicy.ALWAYS))
            .validate(instance)

        assertThat(actual).isNotNull()
    }

    @Test
    fun `custom format`() {
        val validator = Validator.create(SchemaLoader("""
            {
                "format": "parens"
            }
        """)(), config = ValidatorConfig.builder()
            .additionalFormatValidator("parens") { instance, schema ->
                instance.maybeString { str ->
                    var openCount = 0
                    for (ch in str.value) {
                        if (ch == '(') {
                            ++openCount
                        } else if (ch == ')') {
                            --openCount
                            if (openCount < 0) {
                                return@maybeString FormatValidationFailure(schema, instance)
                            }
                        }
                    }
                    return@maybeString if (openCount != 0) FormatValidationFailure(schema, instance) else null
                }
            }
            .build()
        )

        val noFailure = validator.validate(JsonString("asd((asd))"))
        assertThat(noFailure).isNull()

        val failure = validator.validate(JsonString("asd(((asd))"))
        assertThat(failure).isInstanceOf(FormatValidationFailure::class.java)
        assertThat((failure as FormatValidationFailure).dynamicPath.pointer).isEqualTo(JsonPointer("format"))
    }

    @Test
    fun `format override`() {
        val validator = Validator.create(SchemaLoader("""
            {
                "format": "email"
            }
        """)(), config = ValidatorConfig.builder()
            .additionalFormatValidator("email") { instance, schema ->
                instance.maybeString { if (it.value.endsWith(".com")) null else FormatValidationFailure(schema, instance) }
            }
            .build()
        )

        val noFailure = validator.validate(JsonString("asdasd.com"))
        assertThat(noFailure).isNull()

        val failure = validator.validate(JsonString("erosb@github.io"))
        assertThat(failure).isNotNull()
        assertThat(failure!!.message).isEqualTo("instance does not match format 'email'")
    }
}
