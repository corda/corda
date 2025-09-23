package net.corda.testing.core

import net.corda.core.internal.staticField
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.coretesting.internal.*
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector
import org.junit.jupiter.api.extension.*
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.lang.reflect.Method

/**
 * A JUnit 5 extension that sets up a [SerializationEnvironment] for tests.
 *
 * @param inheritable whether new threads inherit the environment, use sparingly.
 */
class SerializationEnvironmentExtension(
        private val inheritable: Boolean = false
) : BeforeEachCallback, InvocationInterceptor, ParameterResolver {

    companion object {
        init {
            // Hack Artemis's static executor service like the JUnit 4 rule
            InVMConnector::class.staticField<ExecutorService>("executorService").value =
                    rigorousMock<ExecutorService>().also { mockExec ->
                        doAnswer { invocation ->
                            inVMExecutors.computeIfAbsent(effectiveSerializationEnv) {
                                Executors.newCachedThreadPool(testThreadFactory(true))
                            }.execute(invocation.arguments[0] as Runnable)
                        }.whenever(mockExec).execute(any())
                    }
        }
    }

    private lateinit var env: SerializationEnvironment

    val serializationFactory: SerializationFactory
        get() = env.serializationFactory

    override fun beforeEach(context: ExtensionContext) {
        // Eager init of environment before parameter resolution
        env = createTestSerializationEnv()
    }

    /**
     * Wraps the test body in the test serialization environment.
     */
    override fun interceptTestMethod(
            invocation: InvocationInterceptor.Invocation<Void>,
            context: ReflectiveInvocationContext<Method>,
            extensionContext: ExtensionContext
    ) {
        env.asTestContextEnv {
            invocation.proceed()
        }
    }

    /**
     * Allow tests to inject [SerializationFactory] directly as a parameter.
     */
    override fun supportsParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == SerializationFactory::class.java
    }

    override fun resolveParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext
    ): Any {
        return serializationFactory
    }
}