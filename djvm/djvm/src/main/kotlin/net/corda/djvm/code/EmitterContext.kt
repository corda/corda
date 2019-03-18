package net.corda.djvm.code

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.ClassModule
import net.corda.djvm.references.Member
import net.corda.djvm.references.MemberModule

/**
 * The context in which an emitter is invoked.
 *
 * @property analysisContext The context in which a class and its members are processed.
 * @property configuration The configuration to used for the analysis.
 * @property emitterModule A module providing code generation functionality that can be used from within an emitter.
 */
@Suppress("unused")
open class EmitterContext(
        private val analysisContext: AnalysisRuntimeContext,
        private val configuration: AnalysisConfiguration,
        val emitterModule: EmitterModule
) {

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
     * Resolve the sandboxed name of a class or interface.
     */
    fun resolve(typeName: String): String {
        return configuration.classResolver.resolve(typeName)
    }

    /**
     * Set up and execute an emitter block for a particular member.
     */
    inline fun emit(action: EmitterModule.() -> Unit) {
        action(emitterModule)
    }

}