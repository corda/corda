package net.corda.node.services.statemachine

import net.corda.core.flows.*
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

    // Version of the code
    abstract val subFlowVersion: SubFlowVersion

    /**
     * An inlined subflow.
     */
    data class Inlined(override val flowClass: Class<FlowLogic<*>>, override val subFlowVersion: SubFlowVersion) : SubFlow()

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
            val flowInfo: FlowInfo,
            override val subFlowVersion: SubFlowVersion
    ) : SubFlow()

    companion object {
        fun create(flowClass: Class<FlowLogic<*>>, subFlowVersion: SubFlowVersion): Try<SubFlow> {
            // Are we an InitiatingFlow?
            val initiatingAnnotations = getInitiatingFlowAnnotations(flowClass)
            return when (initiatingAnnotations.size) {
                0 -> {
                    Try.Success(Inlined(flowClass, subFlowVersion))
                }
                1 -> {
                    val initiatingAnnotation = initiatingAnnotations[0]
                    val flowContext = FlowInfo(initiatingAnnotation.second.version, flowClass.appName)
                    Try.Success(Initiating(flowClass, initiatingAnnotation.first, flowContext, subFlowVersion))
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
