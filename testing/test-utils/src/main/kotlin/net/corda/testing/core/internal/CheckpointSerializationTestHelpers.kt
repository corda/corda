package net.corda.testing.core.internal

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.staticField
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.testing.common.internal.asContextEnv
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createTestSerializationEnv
import net.corda.testing.internal.inVMExecutors
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.testThreadFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A test checkpoint serialization rule implementation for use in tests.
 *
 * @param inheritable whether new threads inherit the environment, use sparingly.
 */
class CheckpointSerializationEnvironmentRule(private val inheritable: Boolean = false) : TestRule {
    companion object {
        init {
            // Can't turn it off, and it creates threads that do serialization, so hack it:
            InVMConnector::class.staticField<ExecutorService>("threadPoolExecutor").value = rigorousMock<ExecutorService>().also {
                doAnswer {
                    inVMExecutors.computeIfAbsent(effectiveSerializationEnv) {
                        Executors.newCachedThreadPool(testThreadFactory(true)) // Close enough to what InVMConnector makes normally.
                    }.execute(it.arguments[0] as Runnable)
                }.whenever(it).execute(any())
            }
        }

        /** Do not call, instead use [SerializationEnvironmentRule] as a [org.junit.Rule]. */
        fun <T> run(taskLabel: String, task: (SerializationEnvironment) -> T): T {
            return CheckpointSerializationEnvironmentRule().apply { init() }.runTask(task)
        }
    }


    private lateinit var env: SerializationEnvironment

    override fun apply(base: Statement, description: Description): Statement {
        init()
        return object : Statement() {
            override fun evaluate() = runTask { base.evaluate() }
        }
    }

    private fun init() {
        env = createTestSerializationEnv()
    }

    private fun <T> runTask(task: (SerializationEnvironment) -> T): T {
        try {
            return env.asContextEnv(inheritable, task)
        } finally {
            inVMExecutors.remove(env)
        }
    }

    val checkpointSerializationContext get() = env.checkpointContext
    val checkpointSerializer get() = env.checkpointSerializer
}
