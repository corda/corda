package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.minCorrectReplicas
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.testing.node.internal.demorun.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import java.nio.file.Paths

fun main(args: Array<String>) = BFTNotaryCordform().nodeRunner().deployAndRunNodes()

private const val clusterSize = 4 // Minimum size that tolerates a faulty replica.
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
        val clusterAddresses = (0 until clusterSize).map { NetworkHostAndPort("localhost", 11000 + it * 10) }
        fun notaryNode(replicaId: Int, configure: CordformNode.() -> Unit) = node {
            name(notaryNames[replicaId])
            notary(NotaryConfig(validating = false, bftSMaRt = BFTSMaRtConfiguration(replicaId, clusterAddresses)))
            configure()
        }
        notaryNode(0) {
            p2pPort(10009)
            rpcSettings {
                address("localhost:10010")
                adminAddress("localhost:10110")
            }
        }
        notaryNode(1) {
            p2pPort(10013)
            rpcSettings {
                address("localhost:10014")
                adminAddress("localhost:10114")
            }
        }
        notaryNode(2) {
            p2pPort(10017)
            rpcSettings {
                address("localhost:10018")
                adminAddress("localhost:10118")
            }
        }
        notaryNode(3) {
            p2pPort(10021)
            rpcSettings {
                address("localhost:10022")
                adminAddress("localhost:10122")
            }
        }
    }

    override fun setup(context: CordformContext) {
        DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                notaryNames.map { context.baseDirectory(it.toString()) },
                clusterName,
                minCorrectReplicas(clusterSize)
        )
    }
}
