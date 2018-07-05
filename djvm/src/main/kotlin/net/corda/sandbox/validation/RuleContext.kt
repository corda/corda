package net.corda.sandbox.validation

import net.corda.sandbox.analysis.AnalysisRuntimeContext
import net.corda.sandbox.analysis.SourceLocation
import net.corda.sandbox.analysis.Whitelist
import net.corda.sandbox.references.ClassRepresentation
import net.corda.sandbox.references.ClassModule
import net.corda.sandbox.references.Member
import net.corda.sandbox.references.MemberModule

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
     * Classes and packages that should be left untouched.
     */
    val pinnedClasses: Whitelist
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
