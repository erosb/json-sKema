package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormatTest {

    @Test
    fun invalidDate() {
        val schema = FormatSchema("date", UnknownSource)
        val instance = JsonString("asdasd")
        val actual = Validator.forSchema(schema)
            .validate(instance)!!

        assertThat(actual).isEqualTo(FormatValidationFailure(schema, instance))
        assertThat(actual.message).isEqualTo("instance does not match format 'date'")
    }
}
