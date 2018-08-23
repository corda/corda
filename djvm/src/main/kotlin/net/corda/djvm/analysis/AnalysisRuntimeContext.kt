package net.corda.djvm.analysis

import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member

/**
 * The context of a class analysis.
 *
 * @property clazz The class currently being analyzed.
 * @property member The member currently being analyzed.
 * @property location The current source location.
 * @property messages Collection of messages gathered as part of the analysis.
 * @property configuration The configuration used in the analysis.
 */
data class AnalysisRuntimeContext(
        val clazz: ClassRepresentation,
        val member: Member?,
        val location: SourceLocation,
        val messages: MessageCollection,
        val configuration: AnalysisConfiguration
)
