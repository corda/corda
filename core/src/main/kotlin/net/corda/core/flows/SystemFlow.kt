package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Classifies [FlowLogic] classes that are part of the system flows.
 */
@Target(CLASS)
@MustBeDocumented
annotation class SystemFlow