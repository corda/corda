package net.corda.sandbox.validation

import net.corda.sandbox.messages.MessageCollection
import net.corda.sandbox.references.ClassHierarchy

/**
 * The outcome of a reference validation run.
 *
 * @property classes A collection of classes that were loaded during analysis, with their byte code representation.
 * @property messages A collection of messages that were produced during validation.
 */
data class ReferenceValidationSummary(
        val classes: ClassHierarchy,
        val messages: MessageCollection
)