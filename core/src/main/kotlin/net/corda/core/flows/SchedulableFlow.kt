package net.corda.core.flows

import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Any [FlowLogic] which is schedulable and is designed to be invoked by a [net.corda.core.contracts.SchedulableState]
 * must have this annotation. If it's missing [FlowLogicRefFactory.create] will throw an exception when it comes time
 * to schedule the next activity in [net.corda.core.contracts.SchedulableState.nextScheduledActivity].
 */
@Target(CLASS)
@Inherited
@MustBeDocumented
annotation class SchedulableFlow