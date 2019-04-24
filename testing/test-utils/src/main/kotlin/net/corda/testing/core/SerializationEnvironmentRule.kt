package net.corda.testing.core

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.staticField
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal._inheritableContextSerializationEnv
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.testing.internal.*
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector
import org.junit.jupiter.api.extension.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A test serialization rule implementation for use in tests
 *
 * @param inheritable whether new threads inherit the environment, use sparingly.
 */
class SerializationEnvironmentRule(private val inheritable: Boolean = false) : TestRule {
    companion object {
        init {
            ThreadPoolExecutorHack.enable(this)
        }
    }

    private lateinit var env: SerializationEnvironment

    val serializationFactory: SerializationFactory get() = env.serializationFactory

    override fun apply(base: Statement, description: Description): Statement {
        env = createTestSerializationEnv()
        return object : Statement() {
            override fun evaluate() = env.asTestContextEnv(inheritable) { base.evaluate() }
        }
    }
}

class SerializationEnvironmentExtension(private val inheritable: Boolean = false): Extension, BeforeEachCallback, AfterEachCallback {
    companion object {
        init {
            ThreadPoolExecutorHack.enable(this)
        }
    }

    private lateinit var env: SerializationEnvironment

    val serializationFactory: SerializationFactory get() = env.serializationFactory
    val property get() = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv

    override fun beforeEach(context: ExtensionContext?) {
        env = createTestSerializationEnv()
        property.set(env)
    }

    override fun afterEach(context: ExtensionContext?) {
        inVMExecutors.remove(env)
        property.set(null)
    }
}

class CheckpointSerializationEnvironmentExtension(private val inheritable: Boolean = false): Extension, BeforeEachCallback, AfterEachCallback {
    companion object {
        init {
            ThreadPoolExecutorHack.enable(this)
        }
    }

    private lateinit var env: SerializationEnvironment

    val checkpointSerializationContext get() = env.checkpointContext
    val checkpointSerializer get() = env.checkpointSerializer
    val property get() = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv

    override fun beforeEach(context: ExtensionContext?) {
        env = createTestSerializationEnv()
        property.set(env)
    }

    override fun afterEach(context: ExtensionContext?) {
        inVMExecutors.remove(env)
        property.set(null)
    }
}

private object ThreadPoolExecutorHack {
    fun enable(target: Any) {
        // Can't turn it off, and it creates threads that do serialization, so hack it:
        InVMConnector::class.staticField<ExecutorService>("threadPoolExecutor").value = rigorousMock<ExecutorService>().also {
            doAnswer {
                inVMExecutors.computeIfAbsent(effectiveSerializationEnv) {
                    Executors.newCachedThreadPool(target.testThreadFactory(true)) // Close enough to what InVMConnector makes normally.
                }.execute(it.arguments[0] as Runnable)
            }.whenever(it).execute(any())
        }
    }
}