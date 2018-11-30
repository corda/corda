package net.corda.testing.common.internal

import org.assertj.core.api.AbstractThrowableAssert

inline fun checkNotOnClasspath(className: String, errorMessage: () -> Any) {
    try {
        Class.forName(className)
        throw IllegalStateException(errorMessage().toString())
    } catch (e: ClassNotFoundException) {
        // If the class can't be found then we're good!
    }
}

inline fun <reified TYPE : Throwable> AbstractThrowableAssert<*, *>.isInstanceOf(): AbstractThrowableAssert<*, *> = isInstanceOf(TYPE::class.java)
