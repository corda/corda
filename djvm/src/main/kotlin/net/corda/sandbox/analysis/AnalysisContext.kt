package net.corda.sandbox.analysis

import net.corda.sandbox.messages.MessageCollection
import net.corda.sandbox.references.ClassHierarchy
import net.corda.sandbox.references.ReferenceMap
import net.corda.sandbox.source.ClassSource

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