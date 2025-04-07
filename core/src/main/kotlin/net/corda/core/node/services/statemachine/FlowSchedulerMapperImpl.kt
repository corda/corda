package net.corda.core.node.services.statemachine

import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor

/**
 * Default implementation of [FlowSchedulerMapper].
 *
 * This class maps flows to schedulers based on the presence of the `@FlowThreadPool` annotation and the origin of the invocation.
 * If no annotation is present, or if the specified thread pool is not configured, the invocation origin thread pool is used as a fallback.
 *
 * @property threadPoolNameToThreadPoolSizeMap A map of available thread pools and their sizes, used to determine the available thread pools.
 */
class FlowSchedulerMapperImpl(threadPoolNameToThreadPoolSizeMap: Map<String, Int>) : FlowSchedulerMapper {
    private val availablePools = getAvailablePools(threadPoolNameToThreadPoolSizeMap)

    companion object {
        private val log = loggerFor<FlowSchedulerMapperImpl>()
        private const val DEFAULT_POOL = "default"
    }

    private fun getAvailablePools(threadPoolNameToThreadPoolSizeMap: Map<String, Int>): Set<String> {
        val poolsSet = threadPoolNameToThreadPoolSizeMap.keys.toSet() + DEFAULT_POOL

        log.info("Available flow thread pools: $poolsSet")
        return poolsSet
    }

    override fun getScheduler(
            invocationContext: InvocationContext,
            flowLogic: Class<out FlowLogic<*>>,
            ourIdentity: CordaX500Name
    ): String {
        val flowThreadPoolAnnotation = flowLogic.getAnnotation(FlowThreadPool::class.java)
                ?.value

        if (flowThreadPoolAnnotation != null && availablePools.contains(flowThreadPoolAnnotation)) {
            return flowThreadPoolAnnotation
        }
        val sourceThreadPool = when (invocationContext.origin) {
            is InvocationOrigin.RPC -> "RPC-Origin"
            is InvocationOrigin.Peer -> "Peer-Origin"
            else -> DEFAULT_POOL
        }
        if (availablePools.contains(sourceThreadPool)) {
            return sourceThreadPool
        }
        return DEFAULT_POOL
    }
}
