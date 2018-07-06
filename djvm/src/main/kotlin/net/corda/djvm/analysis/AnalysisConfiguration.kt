package net.corda.djvm.analysis

import net.corda.djvm.messages.Severity
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.MemberModule
import java.nio.file.Path

/**
 * The configuration to use for an analysis.
 *
 * @property whitelist The whitelist of class names.
 * @property pinnedClasses Classes and packages to leave untouched (in addition to the whitelist).
 * @property classResolver Functionality used to resolve the qualified name and relevant information about a class.
 * @property minimumSeverityLevel The minimum severity level to log and report.
 * @property classPath The extended class path to use for the analysis.
 * @property analyzePinnedClasses Analyze pinned classes unless covered by the provided whitelist.
 * @property analyzeAnnotations Analyze annotations despite not being explicitly referenced.
 * @property prefixFilters Only record messages where the originating class name matches one of the provided prefixes.
 * If none are provided, all messages will be reported.
 * @property classModule Module for handling evolution of a class hierarchy during analysis.
 * @property memberModule Module for handling the specification and inspection of class members.
 */
class AnalysisConfiguration(
        val whitelist: Whitelist = Whitelist.MINIMAL,
        val pinnedClasses: Whitelist = whitelist + Whitelist.PINNED_CLASSES,
        val classResolver: ClassResolver = ClassResolver(whitelist, pinnedClasses, SANDBOX_PREFIX),
        val minimumSeverityLevel: Severity = Severity.WARNING,
        val classPath: List<Path> = emptyList(),
        val analyzePinnedClasses: Boolean = false,
        val analyzeAnnotations: Boolean = false,
        val prefixFilters: List<String> = emptyList(),
        val classModule: ClassModule = ClassModule(),
        val memberModule: MemberModule = MemberModule()
) {

    companion object {
        /**
         * The package name prefix to use for classes loaded into a sandbox.
         */
        private const val SANDBOX_PREFIX: String = "sandbox/"
    }

}
