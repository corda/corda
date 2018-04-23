/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.RaftConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.node.internal.demorun.*
import java.nio.file.Paths

fun main(args: Array<String>) = RaftNotaryCordform().nodeRunner().deployAndRunNodes()

internal fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map { CordaX500Name("Notary Service $it", "Zurich", "CH") }

private val notaryNames = createNotaryNames(3)

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
class RaftNotaryCordform : CordformDefinition() {
    private val clusterName = CordaX500Name("Raft", "Zurich", "CH")

    init {
        nodesDirectory = Paths.get("build", "nodes", "nodesRaft")
        node {
            name(ALICE_NAME)
            p2pPort(10002)
            rpcSettings {
                address("localhost:10003")
                adminAddress("localhost:10103")
            }
            rpcUsers(notaryDemoUser)
        }
        node {
            name(BOB_NAME)
            p2pPort(10005)
            rpcSettings {
                address("localhost:10006")
                adminAddress("localhost:10106")
            }
        }
        fun notaryNode(index: Int, nodePort: Int, clusterPort: Int? = null, configure: CordformNode.() -> Unit) = node {
            name(notaryNames[index])
            val clusterAddresses = if (clusterPort != null ) listOf(NetworkHostAndPort("localhost", clusterPort)) else emptyList()
            notary(NotaryConfig(validating = true, raft = RaftConfig(NetworkHostAndPort("localhost", nodePort), clusterAddresses)))
            configure()
        }
        notaryNode(0, 10008) {
            p2pPort(10009)
            rpcSettings {
                address("localhost:10010")
                adminAddress("localhost:10110")
            }
        }
        notaryNode(1, 10012, 10008) {
            p2pPort(10013)
            rpcSettings {
                address("localhost:10014")
                adminAddress("localhost:10114")
            }
        }
        notaryNode(2, 10016, 10008) {
            p2pPort(10017)
            rpcSettings {
                address("localhost:10018")
                adminAddress("localhost:10118")
            }
        }
    }

    override fun setup(context: CordformContext) {
        DevIdentityGenerator.generateDistributedNotarySingularIdentity(
                notaryNames.map { context.baseDirectory(it.toString()) },
                clusterName
        )
    }
}
