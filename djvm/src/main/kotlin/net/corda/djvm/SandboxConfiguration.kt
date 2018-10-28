package net.corda.djvm

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.code.DefinitionProvider
import net.corda.djvm.code.EMIT_TRACING
import net.corda.djvm.code.Emitter
import net.corda.djvm.execution.ExecutionProfile
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.rules.Rule
import net.corda.djvm.utilities.Discovery

/**
 * Configuration to use for the deterministic sandbox.
 *
 * @property rules The rules to apply during the analysis phase.
 * @property emitters The code emitters / re-writers to apply to all loaded classes.
 * @property definitionProviders The meta-data providers to apply to class and member definitions.
 * @property executionProfile The execution profile to use in the sandbox.
 * @property analysisConfiguration The configuration used in the analysis of classes.
 * @property parentClassLoader The [SandboxClassLoader] that this sandbox will use as a parent.
 */
class SandboxConfiguration private constructor(
        val rules: List<Rule>,
        val emitters: List<Emitter>,
        val definitionProviders: List<DefinitionProvider>,
        val executionProfile: ExecutionProfile,
        val analysisConfiguration: AnalysisConfiguration,
        val parentClassLoader: SandboxClassLoader?
) {
    @Suppress("unused")
    companion object {
        /**
         * Default configuration for the deterministic sandbox.
         */
        @JvmField
        val DEFAULT = SandboxConfiguration.of()

        /**
         * Configuration with no emitters, rules, meta-data providers or runtime thresholds.
         */
        @JvmField
        val EMPTY = SandboxConfiguration.of(
                ExecutionProfile.UNLIMITED, emptyList(), emptyList(), emptyList()
        )

        /**
         * Create a sandbox configuration where one or more properties deviates from the default.
         */
        fun of(
                profile: ExecutionProfile = ExecutionProfile.DEFAULT,
                rules: List<Rule> = Discovery.find(),
                emitters: List<Emitter>? = null,
                definitionProviders: List<DefinitionProvider> = Discovery.find(),
                enableTracing: Boolean = true,
                analysisConfiguration: AnalysisConfiguration = AnalysisConfiguration.createRoot(),
                parentClassLoader: SandboxClassLoader? = null
        ) = SandboxConfiguration(
                executionProfile = profile,
                rules = rules,
                emitters = (emitters ?: Discovery.find()).filter {
                    enableTracing || it.priority > EMIT_TRACING
                },
                definitionProviders = definitionProviders,
                analysisConfiguration = analysisConfiguration,
                parentClassLoader = parentClassLoader
        )
    }
}
