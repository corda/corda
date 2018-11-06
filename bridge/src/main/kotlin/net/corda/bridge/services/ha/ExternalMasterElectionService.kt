package net.corda.bridge.services.ha

import net.corda.bridge.services.api.BridgeArtemisConnectionService
import net.corda.bridge.services.api.BridgeMasterService
import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.zookeeper.CordaLeaderListener
import net.corda.nodeapi.internal.zookeeper.ZkClient
import net.corda.nodeapi.internal.zookeeper.ZkLeader
import rx.Subscription
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Election service which uses ZooKeeper for master election purposes.
 */
class ExternalMasterElectionService(private val conf: FirewallConfiguration,
                                    private val auditService: FirewallAuditService,
                                    artemisService: BridgeArtemisConnectionService,
                                    private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeMasterService, ServiceStateSupport by stateHelper {

    private var haElector: ZkLeader? = null
    private var leaderListener: CordaLeaderListener? = null
    private var statusSubscriber: Subscription? = null
    private val statusFollower = ServiceStateCombiner(listOf(artemisService))

    private val activeTransitionLock = ReentrantLock()

    private companion object {
        private val log = contextLogger()
    }

    init {
        require(conf.haConfig != null) { "Undefined HA Config" }
        require(conf.haConfig!!.haConnectionString.split(',').all { it.startsWith("zk://") }) { "Only Zookeeper HA mode 'zk://IPADDR:PORT supported" }
    }

    private fun becomeMaster() {
        auditService.statusChangeEvent("Acquired leadership. Going active")
        stateHelper.active = true
    }

    private fun becomeSlave() {
        log.info("Cancelling leadership")
        stateHelper.active = false
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({ ready ->
            if (ready) {
                log.info("Activating as result of upstream dependencies ready")
                activate()
            } else {
                log.info("Deactivating due to upstream dependencies not ready")
                deactivate()
            }
        }, { log.error("Error in state change", it) })
    }

    override fun stop() {
        auditService.statusChangeEvent("Stop requested")
        deactivate()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
    }

    /**
     * Registers itself for leader election and installs [CordaLeaderListener] hook in case it becomes a leader.
     */
    private fun activate() {
        activeTransitionLock.withLock {
            val zkConf = conf.haConfig!!.haConnectionString.split(',').map { it.replace("zk://", "") }.joinToString(",")
            val leaderPriority = conf.haConfig!!.haPriority
            val hostName: String = InetAddress.getLocalHost().hostName
            val info = ManagementFactory.getRuntimeMXBean()
            val pid = info.name.split("@").firstOrNull()  // TODO Java 9 has better support for this
            val nodeId = "$hostName:$pid"
            val leaderElector = ZkClient(zkConf, conf.haConfig!!.haTopic, nodeId, leaderPriority)
            haElector = leaderElector
            val listener = object : CordaLeaderListener {
                override fun notLeader() {
                    auditService.statusChangeEvent("Leadership loss signalled from Zookeeper")
                    becomeSlave()
                }

                override fun isLeader() {
                    log.info("Zookeeper has signalled leadership acquired.")
                    becomeMaster()
                }
            }
            leaderListener = listener
            leaderElector.addLeadershipListener(listener)
            leaderElector.start()
            auditService.statusChangeEvent("Requesting leadership from Zookeeper")
            leaderElector.requestLeadership()
        }
    }

    /**
     * Becoming slave, relinquishing leadership (if leader) and withdraws from future leader election process.
     */
    private fun deactivate() {
        activeTransitionLock.withLock {
            becomeSlave()
            haElector?.apply {
                leaderListener?.apply { removeLeadershipListener(this) }
                relinquishLeadership()
                close()
            }
            haElector = null
            leaderListener = null
        }
    }
}