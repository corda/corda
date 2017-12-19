package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.minCorrectReplicas
import net.corda.nodeapi.internal.IdentityGenerator
import net.corda.testing.node.internal.demorun.*
import net.corda.testing.ALICE_NAME
import net.corda.testing.BOB_NAME
import java.nio.file.Paths

fun main(args: Array<String>) = BFTNotaryCordform().deployNodes()

private val clusterSize = 4 // Minimum size that tolerates a faulty replica.
private val notaryNames = createNotaryNames(clusterSize)

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
class BFTNotaryCordform : CordformDefinition() {
    private val clusterName = CordaX500Name("BFT", "Zurich", "CH")

    init {
        nodesDirectory = Paths.get("build", "nodes", "nodesBFT")
        node {
            name(ALICE_NAME)
            p2pPort(10002)
            rpcPort(10003)
            rpcUsers(notaryDemoUser)
        }
        node {
            name(BOB_NAME)
            p2pPort(10005)
            rpcPort(10006)
        }
        val clusterAddresses = (0 until clusterSize).map { NetworkHostAndPort("localhost", 11000 + it * 10) }
        fun notaryNode(replicaId: Int, configure: CordformNode.() -> Unit) = node {
            name(notaryNames[replicaId])
            notary(NotaryConfig(validating = false, bftSMaRt = BFTSMaRtConfiguration(replicaId, clusterAddresses)))
            configure()
        }
        notaryNode(0) {
            p2pPort(10009)
            rpcPort(10010)
        }
        notaryNode(1) {
            p2pPort(10013)
            rpcPort(10014)
        }
        notaryNode(2) {
            p2pPort(10017)
            rpcPort(10018)
        }
        notaryNode(3) {
            p2pPort(10021)
            rpcPort(10022)
        }
    }

    override fun setup(context: CordformContext) {
        IdentityGenerator.generateDistributedNotaryIdentity(
                notaryNames.map { context.baseDirectory(it.toString()) },
                clusterName,
                minCorrectReplicas(clusterSize)
        )
    }
}
