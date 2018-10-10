package net.corda.djvm

import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.costing.RuntimeCostSummary
import net.corda.djvm.rewiring.SandboxClassLoader

/**
 * The context in which a sandboxed operation is run.
 *
 * @property configuration The configuration of the sandbox.
 */
class SandboxRuntimeContext(val configuration: SandboxConfiguration) {

    /**
     * The class loader to use inside the sandbox.
     */
    val classLoader: SandboxClassLoader = SandboxClassLoader(
            configuration,
            AnalysisContext.fromConfiguration(configuration.analysisConfiguration)
    )

    /**
     * A summary of the currently accumulated runtime costs (for, e.g., memory allocations, invocations, etc.).
     */
    val runtimeCosts = RuntimeCostSummary(configuration.executionProfile)

    /**
     * Run a set of actions within the provided sandbox context.
     */
    fun use(action: SandboxRuntimeContext.() -> Unit) {
        SandboxRuntimeContext.instance = this
        try {
            action(this)
        } finally {
            threadLocalContext.remove()
        }
    }

    companion object {

        private val threadLocalContext = ThreadLocal<SandboxRuntimeContext?>()

        /**
         * When called from within a sandbox, this returns the context for the current sandbox thread.
         */
        var instance: SandboxRuntimeContext
            get() = threadLocalContext.get()
                    ?: throw IllegalStateException("SandboxContext has not been initialized before use")
            private set(value) {
                threadLocalContext.set(value)
            }

    }

}
