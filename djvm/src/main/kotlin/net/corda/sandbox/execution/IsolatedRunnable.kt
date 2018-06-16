package net.corda.sandbox.execution

import net.corda.sandbox.SandboxConfiguration
import net.corda.sandbox.SandboxRuntimeContext
import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.messages.MessageCollection
import net.corda.sandbox.rewiring.SandboxClassLoader
import net.corda.sandbox.rewiring.SandboxClassLoadingException
import net.corda.sandbox.utilities.loggerFor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/**
 * Container for running a task in an isolated environment.
 */
class IsolatedRunnable(
        private val identifier: String,
        private val configuration: SandboxConfiguration,
        private val context: AnalysisContext
) {

    /**
     * Run an action in an isolated environment.
     */
    fun <T> run(action: IsolatedRunnable.() -> T?): Result<T> {
        val runnable = this
        val threadName = "Sandbox-$identifier-${uniqueIdentifier.getAndIncrement()}"
        val completionLatch = CountDownLatch(1)
        var output: T? = null
        var costs = mapOf<String, Long>()
        var exception: Throwable? = null
        Thread {
            logger.trace("Entering isolated runtime environment...")
            SandboxRuntimeContext(configuration, context.inputClasses).use {
                output = try {
                    action(runnable)
                } catch (ex: Throwable) {
                    logger.error("Exception caught in isolated runtime environment", ex)
                    exception = ex
                    null
                }
                costs = runtimeCosts.summary
            }
            logger.trace("Exiting isolated runtime environment...")
            completionLatch.countDown()
        }.apply {
            name = threadName
            isDaemon = true
            start()
        }
        completionLatch.await()
        val messages = exception.let {
            when (it) {
                is SandboxClassLoadingException -> it.messages
                is SandboxException -> {
                    when (it.exception) {
                        is SandboxClassLoadingException -> it.exception.messages
                        else -> null
                    }
                }
                else -> null
            }
        } ?: MessageCollection()
        return Result(threadName, output, costs, messages, exception)
    }

    /**
     * The result of a run of an [IsolatedRunnable].
     *
     * @property identifier The identifier of the [IsolatedRunnable].
     * @property output The result of the run, if successful.
     * @property costs Captured runtime costs as reported at the end of the run.
     * @property messages The messages collated during the run.
     * @property exception This holds any exceptions that might get thrown during execution.
     */
    data class Result<T>(
            val identifier: String,
            val output: T?,
            val costs: Map<String, Long>,
            val messages: MessageCollection,
            val exception: Throwable?
    )

    /**
     * The class loader to use for loading the [SandboxedRunnable] and any referenced code in [SandboxExecutor.run].
     */
    val classLoader: SandboxClassLoader
        get() = SandboxRuntimeContext.INSTANCE.classLoader

    private companion object {

        /**
         * An atomically incrementing identifier used to uniquely identify each runnable.
         */
        private val uniqueIdentifier = AtomicLong(0)

        private val logger = loggerFor<IsolatedRunnable>()

    }

}