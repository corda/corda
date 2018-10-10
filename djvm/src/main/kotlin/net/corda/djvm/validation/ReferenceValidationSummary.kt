package net.corda.djvm.validation

import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.references.EntityReference

/**
 * The outcome of a reference validation run.
 *
 * @property classes A collection of classes that were loaded during analysis, with their byte code representation.
 * @property messages A collection of messages that were produced during validation.
 * @property classOrigins A mapping of the origin of each class being analyzed.
 */
data class ReferenceValidationSummary(
        val classes: ClassHierarchy,
        val messages: MessageCollection,
        val classOrigins: Map<String, Set<EntityReference>>
)