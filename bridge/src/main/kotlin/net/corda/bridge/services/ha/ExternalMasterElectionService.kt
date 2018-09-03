package net.corda.bridge.services.ha

import net.corda.bridge.services.api.BridgeMasterService
import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.zookeeper.CordaLeaderListener
import net.corda.nodeapi.internal.zookeeper.ZkClient
import net.corda.nodeapi.internal.zookeeper.ZkLeader
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ExternalMasterElectionService(val conf: FirewallConfiguration,
                                    val auditService: FirewallAuditService,
                                    private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeMasterService, ServiceStateSupport by stateHelper {

    private var haElector: ZkLeader? = null
    private var leaderListener: CordaLeaderListener? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var becomeMasterFuture: ScheduledFuture<*>? = null

    companion object {
        val log = contextLogger()
        const val DELAYED_LEADER_START = 5000L
    }

    init {
        require(conf.haConfig != null) { "Undefined HA Config" }
        require(conf.haConfig!!.haConnectionString.split(',').all { it.startsWith("zk://") }) { "Only Zookeeper HA mode 'zk://IPADDR:PORT supported" }
    }

    private fun becomeMaster() {
        auditService.statusChangeEvent("Acquired leadership. Going active")
        stateHelper.active = true
        becomeMasterFuture = null
    }

    private fun becomeSlave() {
        log.info("Cancelling leadership")
        becomeMasterFuture?.apply {
            cancel(false)
        }
        becomeMasterFuture = null
        stateHelper.active = false
    }

    override fun start() {
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
                log.info("Zookeeper has signalled leadership acquired. Delay master claim for a short period to allow old master to close")
                becomeMasterFuture?.apply {
                    cancel(false)
                }
                becomeMasterFuture = scheduler.schedule(::becomeMaster, DELAYED_LEADER_START, TimeUnit.MILLISECONDS)
            }

        }
        leaderListener = listener
        leaderElector.addLeadershipListener(listener)
        leaderElector.start()
        auditService.statusChangeEvent("Requesting leadership from Zookeeper")
        leaderElector.requestLeadership()
    }

    override fun stop() {
        auditService.statusChangeEvent("Stop requested")
        becomeSlave()
        haElector?.apply {
            if (leaderListener != null) {
                removeLeadershipListener(leaderListener!!)
            }
            relinquishLeadership()
            close()
        }
        haElector = null
        leaderListener = null
    }

}