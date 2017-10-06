package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Any [FlowLogic] which is to be started by the [AppServiceHub] interface from
 * within a [CordaService] must have this annotation. If it's missing the
 * flow will not be allowed to start and an exception will be thrown.
 */
@Target(CLASS)
@MustBeDocumented
annotation class StartableByService