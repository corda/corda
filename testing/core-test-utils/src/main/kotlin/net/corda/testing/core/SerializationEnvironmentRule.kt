package net.corda.testing.core

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.staticField
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.coretesting.internal.asTestContextEnv
import net.corda.coretesting.internal.createTestSerializationEnv
import net.corda.coretesting.internal.inVMExecutors
import net.corda.coretesting.internal.rigorousMock
import net.corda.coretesting.internal.testThreadFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector
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
            // Can't turn it off, and it creates threads that do serialization, so hack it:
            InVMConnector::class.staticField<ExecutorService>("threadPoolExecutor").value = rigorousMock<ExecutorService>()
                    .also {
                doAnswer {
                    inVMExecutors.computeIfAbsent(effectiveSerializationEnv) {
                        Executors.newCachedThreadPool(testThreadFactory(true)) // Close enough to what InVMConnector makes normally.
                    }.execute(it.arguments[0] as Runnable)
                }.whenever(it).execute(any())
            }
        }
    }

    private lateinit var env: SerializationEnvironment

    val serializationFactory: SerializationFactory get() = env.serializationFactory

    override fun apply(base: Statement, description: Description): Statement {
        env = createTestSerializationEnv()
        return object : Statement() {
            override fun evaluate() = env.asTestContextEnv { base.evaluate() }
        }
    }
}
