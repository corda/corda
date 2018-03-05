package net.corda.core.node.startup

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Annotate any flow that needs to execute on node startup
 * The flow must have a no-args constructor
 * The flow may be run multiple times in a failure scenario - it must be able to detect the condition where it has already run
 *
 * The annotated class must be a SubClass of [net.corda.core.flows.FlowLogic]
 */

@Target(CLASS, FUNCTION)
annotation class OnNodeStartup
