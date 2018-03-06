/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.utilities.Try

/**
 * A [SubFlow] contains metadata about a currently executing sub-flow. At any point the flow execution is
 * characterised with a stack of [SubFlow]s. This stack is used to determine the initiating-initiated flow mapping.
 *
 * Note that Initiat*ed*ness is an orthogonal property of the top-level subflow, so we don't store any information about
 * it here.
 */
sealed class SubFlow {
    abstract val flowClass: Class<out FlowLogic<*>>

    /**
     * An inlined subflow.
     */
    data class Inlined(override val flowClass: Class<FlowLogic<*>>) : SubFlow()

    /**
     * An initiating subflow.
     * @param [flowClass] the concrete class of the subflow.
     * @param [classToInitiateWith] an ancestor class of [flowClass] with the [InitiatingFlow] annotation, to be sent
     *   to the initiated side.
     * @param flowInfo the [FlowInfo] associated with the initiating flow.
     */
    data class Initiating(
            override val flowClass: Class<FlowLogic<*>>,
            val classToInitiateWith: Class<in FlowLogic<*>>,
            val flowInfo: FlowInfo
    ) : SubFlow()

    companion object {
        fun create(flowClass: Class<FlowLogic<*>>): Try<SubFlow> {
            // Are we an InitiatingFlow?
            val initiatingAnnotations = getInitiatingFlowAnnotations(flowClass)
            return when (initiatingAnnotations.size) {
                0 -> {
                    Try.Success(Inlined(flowClass))
                }
                1 -> {
                    val initiatingAnnotation = initiatingAnnotations[0]
                    val flowContext = FlowInfo(initiatingAnnotation.second.version, flowClass.appName)
                    Try.Success(Initiating(flowClass, initiatingAnnotation.first, flowContext))
                }
                else -> {
                    Try.Failure(IllegalArgumentException("${InitiatingFlow::class.java.name} can only be annotated " +
                            "once, however the following classes all have the annotation: " +
                            "${initiatingAnnotations.map { it.first }}"))
                }
            }
        }

        private fun <C> getSuperClasses(clazz: Class<C>): List<Class<in C>> {
            var currentClass: Class<in C>? = clazz
            val result = ArrayList<Class<in C>>()
            while (currentClass != null) {
                result.add(currentClass)
                currentClass = currentClass.superclass
            }
            return result
        }

        private fun getInitiatingFlowAnnotations(flowClass: Class<FlowLogic<*>>): List<Pair<Class<in FlowLogic<*>>, InitiatingFlow>> {
            return getSuperClasses(flowClass).mapNotNull { clazz ->
                val initiatingAnnotation = clazz.getDeclaredAnnotation(InitiatingFlow::class.java)
                initiatingAnnotation?.let { Pair(clazz, it) }
            }
        }
    }
}