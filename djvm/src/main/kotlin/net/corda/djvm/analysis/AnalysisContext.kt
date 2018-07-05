package net.corda.djvm.analysis

import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.references.ClassHierarchy
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

    }

}