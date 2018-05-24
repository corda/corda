package net.corda.sandbox

import net.corda.sandbox.analysis.AnalysisConfiguration
import net.corda.sandbox.code.DefinitionProvider
import net.corda.sandbox.code.Emitter
import net.corda.sandbox.execution.ExecutionProfile
import net.corda.sandbox.rules.Rule
import net.corda.sandbox.utilities.Discovery

/**
 * Configuration to use for the deterministic sandbox.
 *
 * @property rules The rules to apply during the analysis phase.
 * @property emitters The code emitters / re-writers to apply to all loaded classes.
 * @property definitionProviders The meta-data providers to apply to class and member definitions.
 * @property executionProfile The execution profile to use in the sandbox.
 * @property analysisConfiguration The configuration used in the analysis of classes.
 */
@Suppress("unused")
class SandboxConfiguration private constructor(
        val rules: List<Rule>,
        val emitters: List<Emitter>,
        val definitionProviders: List<DefinitionProvider>,
        val executionProfile: ExecutionProfile,
        val analysisConfiguration: AnalysisConfiguration
) {

    companion object {

        /**
         * Default configuration for the deterministic sandbox.
         */
        val DEFAULT = SandboxConfiguration.of()

        /**
         * Configuration with no emitters, rules, meta-data providers or runtime thresholds.
         */
        val EMPTY = SandboxConfiguration.of(
                ExecutionProfile.UNLIMITED, emptyList(), emptyList(), emptyList()
        )

        /**
         * Create a sandbox configuration where one or more properties deviates from the default.
         */
        fun of(
                profile: ExecutionProfile? = null,
                rules: List<Rule>? = null,
                emitters: List<Emitter>? = null,
                definitionProviders: List<DefinitionProvider>? = null,
                enableTracing: Boolean = true,
                analysisConfiguration: AnalysisConfiguration? = null
        ) = SandboxConfiguration(
                executionProfile = profile ?: ExecutionProfile.DEFAULT,
                rules = rules ?: Discovery.find(),
                emitters = (emitters ?: Discovery.find()).filter {
                    enableTracing || !it.isTracer
                },
                definitionProviders = definitionProviders ?: Discovery.find(),
                analysisConfiguration = analysisConfiguration ?: AnalysisConfiguration()
        )

    }

}
