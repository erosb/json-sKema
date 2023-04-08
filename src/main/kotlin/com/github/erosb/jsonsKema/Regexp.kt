package com.github.erosb.jsonsKema

import java.util.*
import java.util.regex.Pattern

class RegexpMatchingFailure internal constructor() {
    override fun equals(obj: Any?): Boolean {
        return obj is RegexpMatchingFailure
    }
}

interface Regexp {

    fun patternMatchingFailure(input: String?): RegexpMatchingFailure?
}

internal abstract class AbstractRegexp(asString: String) : Regexp {
    private val asString: String

    init {
        this.asString = Objects.requireNonNull(asString, "asString cannot be null")
    }

    override fun toString(): String {
        return asString
    }
}

internal class JavaUtilRegexp(pattern: String?) : AbstractRegexp(pattern!!) {
    private val pattern: Pattern

    init {
        this.pattern = Pattern.compile(pattern)
    }

    override fun patternMatchingFailure(input: String?): RegexpMatchingFailure? {
        return if (pattern.matcher(input).find()) {
            null
        } else {
            RegexpMatchingFailure()
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is JavaUtilRegexp) return false
        return pattern.pattern() == o.pattern.pattern()
    }

    override fun hashCode(): Int {
        return Objects.hash(pattern)
    }
}

internal interface RegexpFactory {
    fun createHandler(input: String): Regexp
}

internal class JavaUtilRegexpFactory : RegexpFactory {
    override fun createHandler(input: String): Regexp {
        return JavaUtilRegexp(input)
    }
}
