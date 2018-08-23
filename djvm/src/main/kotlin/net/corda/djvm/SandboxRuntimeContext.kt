package net.corda.djvm

import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.costing.RuntimeCostSummary
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.source.ClassSource

/**
 * The context in which a sandboxed operation is run.
 *
 * @property configuration The configuration of the sandbox.
 * @property inputClasses The classes passed in for analysis.
 */
class SandboxRuntimeContext(
        val configuration: SandboxConfiguration,
        private val inputClasses: List<ClassSource>
) {

    /**
     * The class loader to use inside the sandbox.
     */
    val classLoader: SandboxClassLoader = SandboxClassLoader(
            configuration,
            AnalysisContext.fromConfiguration(configuration.analysisConfiguration, inputClasses)
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
            this.action()
        } finally {
            threadLocalContext.remove()
        }
    }

    companion object {

        private val threadLocalContext = object : ThreadLocal<SandboxRuntimeContext?>() {
            override fun initialValue(): SandboxRuntimeContext? = null
        }

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
