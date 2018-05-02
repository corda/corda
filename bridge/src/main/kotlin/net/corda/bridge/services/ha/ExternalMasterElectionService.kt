/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.ha

import net.corda.bridge.services.api.BridgeAuditService
import net.corda.bridge.services.api.BridgeConfiguration
import net.corda.bridge.services.api.BridgeMasterService
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.zookeeper.CordaLeaderListener
import net.corda.nodeapi.internal.zookeeper.ZkClient
import net.corda.nodeapi.internal.zookeeper.ZkLeader
import java.lang.management.ManagementFactory
import java.net.InetAddress

class ExternalMasterElectionService(val conf: BridgeConfiguration,
                                    val auditService: BridgeAuditService,
                                    private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeMasterService, ServiceStateSupport by stateHelper {

    private var haElector: ZkLeader? = null
    private var leaderListener: CordaLeaderListener? = null

    companion object {
        val log = contextLogger()
    }

    init {
        require(conf.haConfig != null) { "Undefined HA Config" }
        require(conf.haConfig!!.haConnectionString.split(',').all { it.startsWith("zk://") }) { "Only Zookeeper HA mode 'zk://IPADDR:PORT supported" }
    }

    override fun start() {
        val zkConf = conf.haConfig!!.haConnectionString.split(',').map { it.replace("zk://", "") }.joinToString(",")
        val leaderPriority = conf.haConfig!!.haPriority
        val hostName: String = InetAddress.getLocalHost().hostName
        val info = ManagementFactory.getRuntimeMXBean()
        val pid = info.name.split("@").firstOrNull()  // TODO Java 9 has better support for this
        val nodeId = "$hostName:$pid"
        val leaderElector = ZkClient(zkConf, "/bridge/ha", nodeId, leaderPriority)
        haElector = leaderElector
        val listener = object : CordaLeaderListener {
            override fun notLeader() {
                auditService.statusChangeEvent("Loss of leadership signalled by Zookeeper")
                stateHelper.active = false
            }

            override fun isLeader() {
                auditService.statusChangeEvent("Acquired leadership from Zookeeper. Going active")
                stateHelper.active = true
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
        stateHelper.active = false
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