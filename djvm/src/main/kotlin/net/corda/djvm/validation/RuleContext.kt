package net.corda.djvm.validation

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.Member
import net.corda.djvm.references.MemberModule

/**
 * The context in which a rule is validated.
 *
 * @property analysisContext The context in which a class and its members are analyzed.
 */
@Suppress("unused")
open class RuleContext(
        private val analysisContext: AnalysisRuntimeContext
) : ConstraintProvider(analysisContext) {

    /**
     * The class currently being analysed.
     */
    val clazz: ClassRepresentation
        get() = analysisContext.clazz

    /**
     * The member currently being analysed, if any.
     */
    val member: Member?
        get() = analysisContext.member

    /**
     * The current source location.
     */
    val location: SourceLocation
        get() = analysisContext.location

    /**
     * The configured whitelist.
     */
    val whitelist: Whitelist
        get() = analysisContext.configuration.whitelist

    /**
     * Classes that have been explicitly defined in the sandbox namespace.
     */
    val pinnedClasses: Set<String>
        get() = analysisContext.configuration.pinnedClasses

    /**
     * Utilities for dealing with classes.
     */
    val classModule: ClassModule
        get() = analysisContext.configuration.classModule

    /**
     * Utilities for dealing with members.
     */
    val memberModule: MemberModule
        get() = analysisContext.configuration.memberModule

    /**
     * Set up and execute a rule validation block.
     */
    fun validate(action: RuleContext.() -> Unit) {
        action(this)
    }

}
