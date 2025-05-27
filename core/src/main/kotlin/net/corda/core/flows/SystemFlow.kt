package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Classifies [FlowLogic] classes that are part of the system flows.
 *
 * @property [supersedes] The [String] class name of the flow that this flow supersedes.
 */
@Target(CLASS)
@MustBeDocumented
annotation class SystemFlow(val supersedes: String = "")