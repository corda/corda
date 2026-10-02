package net.corda.testing.common.internal

import net.corda.core.utilities.NetworkHostAndPort
import org.assertj.core.api.AbstractThrowableAssert
import java.net.Socket
import java.net.SocketException

inline fun checkNotOnClasspath(className: String, errorMessage: () -> Any) {
    try {
        Class.forName(className)
        throw IllegalStateException(errorMessage().toString())
    } catch (e: ClassNotFoundException) {
        // If the class can't be found then we're good!
    }
}

inline fun <reified TYPE : Throwable> AbstractThrowableAssert<*, *>.isInstanceOf(): AbstractThrowableAssert<*, *> = isInstanceOf(TYPE::class.java)

fun NetworkHostAndPort.isListening(): Boolean = isListening(host, port)

fun isListening(host: String, port: Int): Boolean {
    return try {
        Socket(host, port).use { true }
    } catch (_: SocketException) {
        false
    }
}
