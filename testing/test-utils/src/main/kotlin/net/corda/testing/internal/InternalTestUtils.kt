package net.corda.testing.internal

import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.serialization.amqp.AMQP_ENABLED
import org.mockito.Mockito
import org.mockito.internal.stubbing.answers.ThrowsException
import java.lang.reflect.Modifier
import java.util.*

@Suppress("unused")
inline fun <reified T : Any> T.kryoSpecific(reason: String, function: () -> Unit) = if (!AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring Kryo specific test, reason: $reason")
}

@Suppress("unused")
inline fun <reified T : Any> T.amqpSpecific(reason: String, function: () -> Unit) = if (AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring AMQP specific test, reason: $reason")
}

/**
 * A method on a mock was called, but no behaviour was previously specified for that method.
 * You can use [com.nhaarman.mockito_kotlin.doReturn] or similar to specify behaviour, see Mockito documentation for details.
 */
class UndefinedMockBehaviorException(message: String) : RuntimeException(message)

inline fun <reified T : Any> rigorousMock() = rigorousMock(T::class.java)
/**
 * Create a Mockito mock that has [UndefinedMockBehaviorException] as the default behaviour of all abstract methods,
 * and [org.mockito.invocation.InvocationOnMock.callRealMethod] as the default for all concrete methods.
 * @param T the type to mock. Note if you want concrete methods of a Kotlin interface to be invoked,
 * it won't work unless you mock a (trivial) abstract implementation of that interface instead.
 */
fun <T> rigorousMock(clazz: Class<T>): T = Mockito.mock(clazz) {
    if (Modifier.isAbstract(it.method.modifiers)) {
        // Use ThrowsException to hack the stack trace, and lazily so we can customise the message:
        ThrowsException(UndefinedMockBehaviorException("Please specify what should happen when '${it.method}' is called, or don't call it. Args: ${Arrays.toString(it.arguments)}")).answer(it)
    } else {
        it.callRealMethod()
    }
}
