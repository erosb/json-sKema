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
    @Disabled
    fun `date-time valid leap second at UTC`() {
        val instance = JsonString("1990-02-31T15:59:59.123-08:00")

        val actual = Validator.create(DATE_TIME_SCHEMA, ValidatorConfig(validateFormat =  FormatValidationPolicy.ALWAYS))
            .validate(instance)

        assertThat(actual).isNull()
    }
}
