package net.corda.djvm.analysis

import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.references.EntityReference
import net.corda.djvm.references.ReferenceMap
import net.corda.djvm.source.ClassSource

/**
 * The context in which one or more classes are analysed.
 *
 * @property messages Collection of messages gathered as part of the analysis.
 * @property classes List of class definitions that have been analyzed.
 * @property references A collection of all referenced members found during analysis together with the locations from
 * where each member has been accessed or invoked.
 * @property inputClasses The classes passed in for analysis.
 */
class AnalysisContext private constructor(
        val messages: MessageCollection,
        val classes: ClassHierarchy,
        val references: ReferenceMap,
        val inputClasses: List<ClassSource>
) {

    private val origins = mutableMapOf<String, MutableSet<EntityReference>>()

    /**
     * Record a class origin in the current analysis context.
     */
    fun recordClassOrigin(name: String, origin: EntityReference) {
        origins.getOrPut(name.normalize()) { mutableSetOf() }.add(origin)
    }

    /**
     * Map of class origins. The resulting set represents the types referencing the class in question.
     */
    val classOrigins: Map<String, Set<EntityReference>>
        get() = origins

    companion object {

        /**
         * Create a new analysis context from provided configuration.
         */
        fun fromConfiguration(configuration: AnalysisConfiguration, classes: List<ClassSource>): AnalysisContext {
            return AnalysisContext(
                    MessageCollection(configuration.minimumSeverityLevel, configuration.prefixFilters),
                    ClassHierarchy(configuration.classModule, configuration.memberModule),
                    ReferenceMap(configuration.classModule),
                    classes
            )
        }

        /**
         * Local extension method for normalizing a class name.
         */
        private fun String.normalize() = this.replace("/", ".")

    }

}