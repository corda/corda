package net.corda.core.flows.scheduler.mapper

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * This annotation specifies the preferred thread pool to be used for running the flow.
 * Note that this does not guarantee the flow will run in the specified thread pool,
 * as the thread pool might not be configured.
 *
 * N.B.: This is only a hint indicating the preferred thread pool.
 */
@Target(CLASS)
@MustBeDocumented
annotation class FlowThreadPool(val value: String)
