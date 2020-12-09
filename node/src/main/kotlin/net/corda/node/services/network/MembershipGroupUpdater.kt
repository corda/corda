package net.corda.node.services.network

import com.google.common.util.concurrent.MoreExecutors
import net.corda.core.flows.StateMachineRunId
import net.corda.core.node.MemberStatus
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.minutes
import net.corda.node.services.api.MembershipGroupCacheInternal
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.NamedThreadFactory
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/* TODO[MGM]: Should be only started from group manager */
class MembershipGroupUpdater(private val serviceHub: ServiceHubInternal, private val stateMachineManager: StateMachineManager) : AutoCloseable {

    companion object {
        private val logger = contextLogger()
        private val updateInterval = 1.minutes
    }

    private val membershipGroupCache: MembershipGroupCacheInternal = serviceHub.membershipGroupCache
    private val membershipGroupService by lazy { serviceHub.cordaService(FlowMembershipGroupService::class.java) }

    private val membershipUpdatePusher = ScheduledThreadPoolExecutor(1, NamedThreadFactory("Membership Group Updater Thread")).apply {
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }
    private val activeFlowIds = mutableSetOf<StateMachineRunId>()

    override fun close() {
        MoreExecutors.shutdownAndAwaitTermination(membershipUpdatePusher, updateInterval.toMillis(), TimeUnit.MILLISECONDS)
    }

    /** Bootstraps the updater to put it in the correct state for day to day business. **/
    fun start() {
        startMembershipUpdatePusher()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startMembershipUpdatePusher() {
        membershipUpdatePusher.submit(object : Runnable {
            override fun run() {
                try {
                    logger.info("Updating membership group caches")
                    updateMembersCaches()
                } catch (e: Exception) {
                    logger.warn("Error encountered while updating members' caches, will retry in $updateInterval", e)
                }
                // Schedule the next update
                membershipUpdatePusher.schedule(this, updateInterval.toMillis(), TimeUnit.MILLISECONDS)
            }
        })
    }

    private fun updateMembersCaches() {
        // kill all non-finished sync flows
        with(activeFlowIds) {
            forEach { stateMachineManager.killFlow(it) }
            clear()
        }
        logger.info("Killed all non-finished sync flows")

        membershipGroupCache.allMembers.filterNot {
            it.status == MemberStatus.PENDING
        }.forEach {
            val flowHandle = membershipGroupService.syncMembershipGroupCache(it.party)
            activeFlowIds.add(flowHandle.id)
        }
        logger.info("Initiated sync flows for all managed groups")
    }
}
