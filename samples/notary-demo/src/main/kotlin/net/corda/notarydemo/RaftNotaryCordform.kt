package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.RaftConfig
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.internal.demorun.*

fun main(args: Array<String>) = RaftNotaryCordform.runNodes()

internal fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map { CordaX500Name("Notary Service $it", "Zurich", "CH") }

private val notaryNames = createNotaryNames(3)

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
object RaftNotaryCordform : CordformDefinition("build" / "notary-demo-nodes") {
    private val clusterName = CordaX500Name(RaftValidatingNotaryService.id, "Raft", "Zurich", "CH")

    init {
        node {
            name(ALICE.name)
            p2pPort(10002)
            rpcPort(10003)
            rpcUsers(notaryDemoUser)
        }
        node {
            name(BOB.name)
            p2pPort(10005)
            rpcPort(10006)
        }
        fun notaryNode(index: Int, nodePort: Int, clusterPort: Int? = null, configure: CordformNode.() -> Unit) = node {
            name(notaryNames[index])
            val clusterAddresses = if (clusterPort != null ) listOf(NetworkHostAndPort("localhost", clusterPort)) else emptyList()
            notary(NotaryConfig(validating = true, raft = RaftConfig(NetworkHostAndPort("localhost", nodePort), clusterAddresses)))
            configure()
        }
        notaryNode(0, 10008) {
            p2pPort(10009)
            rpcPort(10010)
        }
        notaryNode(1, 10012, 10008) {
            p2pPort(10013)
            rpcPort(10014)
        }
        notaryNode(2, 10016, 10008) {
            p2pPort(10017)
            rpcPort(10018)
        }
    }

    override fun setup(context: CordformContext) {
        ServiceIdentityGenerator.generateToDisk(notaryNames.map { context.baseDirectory(it.toString()) }, clusterName)
    }
}
