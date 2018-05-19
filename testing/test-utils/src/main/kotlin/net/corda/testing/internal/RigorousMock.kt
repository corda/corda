package net.corda.testing.internal

import com.nhaarman.mockito_kotlin.doAnswer
import net.corda.core.utilities.contextLogger
import org.mockito.Mockito
import org.mockito.exceptions.base.MockitoException
import org.mockito.internal.stubbing.answers.ThrowsException
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A method on a mock was called, but no behaviour was previously specified for that method.
 * You can use [com.nhaarman.mockito_kotlin.doReturn] or similar to specify behaviour, see Mockito documentation for details.
 */
class UndefinedMockBehaviorException(message: String) : RuntimeException(message)

inline fun <reified T : Any> spectator() = spectator(T::class.java)
inline fun <reified T : Any> rigorousMock() = rigorousMock(T::class.java)
inline fun <reified T : Any> participant() = participant(T::class.java)
/**
 * Create a Mockito mock where void methods do nothing, and any method of mockable return type will return another spectator,
 * and multiple calls to such a method with equal args will return the same spectator.
 * This is useful for an inconsequential service such as metrics or logging.
 * Unlike plain old Mockito, methods that return primitives and unmockable types such as [String] remain unimplemented.
 * Use sparingly, as any invalid behaviour caused by the implicitly-created spectators is likely to be difficult to diagnose.
 * As in the other profiles, the exception is [toString] which has a simple reliable implementation for ease of debugging.
 */
fun <T> spectator(clazz: Class<out T>) = Mockito.mock(clazz, SpectatorDefaultAnswer())!!

/**
 * Create a Mockito mock that inherits the implementations of all concrete methods from the given type.
 * In particular this is convenient for mocking a Kotlin interface via a trivial abstract class.
 * As in the other profiles, the exception is [toString] which has a simple reliable implementation for ease of debugging.
 */
fun <T> rigorousMock(clazz: Class<out T>) = Mockito.mock(clazz, RigorousMockDefaultAnswer)!!

/**
 * Create a Mockito mock where all methods throw [UndefinedMockBehaviorException].
 * Such mocks are useful when testing a grey box, for complete visibility and control over what methods it calls.
 * As in the other profiles, the exception is [toString] which has a simple reliable implementation for ease of debugging.
 */
fun <T> participant(clazz: Class<out T>) = Mockito.mock(clazz, ParticipantDefaultAnswer)!!

private abstract class DefaultAnswer : Answer<Any?> {
    internal abstract fun answerImpl(invocation: InvocationOnMock): Any?
    override fun answer(invocation: InvocationOnMock): Any? {
        val method = invocation.method
        if (method.name == "toString" && method.parameterCount == 0) {
            // Regular toString doesn't cache so neither do we:
            val mock = invocation.mock
            return "${mock.javaClass.simpleName}@${Integer.toHexString(mock.hashCode())}"
        }
        return answerImpl(invocation)
    }
}

private class SpectatorDefaultAnswer : DefaultAnswer() {
    private companion object {
        private val log = contextLogger()
    }

    private class MethodInfo(invocation: InvocationOnMock) {
        // FIXME LATER: The type resolution code probably doesn't cover all cases.
        private val type = run {
            val method = invocation.method
            fun resolveType(context: Type, type: Type): Type {
                context as? ParameterizedType ?: return type
                val clazz = context.rawType as Class<*>
                return context.actualTypeArguments[clazz.typeParameters.indexOf(resolveType(clazz.genericSuperclass, type))]
            }
            resolveType(invocation.mock.javaClass.genericSuperclass, method.genericReturnType) as? Class<*>
                    ?: method.returnType!!
        }

        private fun newSpectator(invocation: InvocationOnMock) = spectator(type)!!.also { log.info("New spectator {} for: {}", it, invocation.arguments) }
        private val spectators = try {
            val first = newSpectator(invocation)
            ConcurrentHashMap<InvocationOnMock, Any>().apply { put(invocation, first) }
        } catch (e: MockitoException) {
            null // A few types can't be mocked e.g. String.
        }

        internal fun spectator(invocation: InvocationOnMock) = spectators?.computeIfAbsent(invocation, ::newSpectator)
    }

    private val methods by lazy { ConcurrentHashMap<Method, MethodInfo>() }
    override fun answerImpl(invocation: InvocationOnMock): Any? {
        invocation.method.returnType.let {
            it == Void.TYPE && return null
            it.isPrimitive && return ParticipantDefaultAnswer.answerImpl(invocation)
        }
        return methods.computeIfAbsent(invocation.method) { MethodInfo(invocation) }.spectator(invocation)
                ?: ParticipantDefaultAnswer.answerImpl(invocation)
    }
}

private object RigorousMockDefaultAnswer : DefaultAnswer() {
    override fun answerImpl(invocation: InvocationOnMock): Any? {
        return if (Modifier.isAbstract(invocation.method.modifiers)) ParticipantDefaultAnswer.answerImpl(invocation) else invocation.callRealMethod()
    }
}

private object ParticipantDefaultAnswer : DefaultAnswer() {
    override fun answerImpl(invocation: InvocationOnMock): Any? {
        // Use ThrowsException to hack the stack trace, and lazily so we can customise the message:
        return ThrowsException(UndefinedMockBehaviorException(
                "Please specify what should happen when '${invocation.method}' is called, or don't call it. Args: ${Arrays.toString(invocation.arguments)}"))
                .answer(invocation)
    }
}

/**
 * Application of [doAnswer] that gets a value from the given [map] using the arg at [argIndex] as key.
 * This should be used instead of concurrent stubbing, which leads to Mockito errors.
 */
fun doLookup(map: Map<*, *>, argIndex: Int = 0) = doAnswer { map[it.getArgument<Any?>(argIndex)] }
