package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ServiceInfo
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.internal.demorun.*

fun main(args: Array<String>) = RaftNotaryCordform.runNodes()

internal fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map { CordaX500Name(commonName ="Notary Service $it", organisationUnit = "corda", organisation = "R3 Ltd", locality = "Zurich", state = null, country = "CH") }

private val notaryNames = createNotaryNames(3)

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
object RaftNotaryCordform : CordformDefinition("build" / "notary-demo-nodes", notaryNames[0].toString()) {
    private val clusterName = CordaX500Name(organisation = "Raft", locality = "Zurich", country = "CH")
    private val advertisedService = ServiceInfo(RaftValidatingNotaryService.type, clusterName)

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
        fun notaryNode(index: Int, configure: CordformNode.() -> Unit) = node {
            name(notaryNames[index])
            advertisedServices(advertisedService)
            configure()
        }
        notaryNode(0) {
            notaryNodePort(10008)
            p2pPort(10009)
            rpcPort(10010)
        }
        val clusterAddress = NetworkHostAndPort("localhost", 10008) // Otherwise each notary forms its own cluster.
        notaryNode(1) {
            notaryNodePort(10012)
            p2pPort(10013)
            rpcPort(10014)
            notaryClusterAddresses(clusterAddress)
        }
        notaryNode(2) {
            notaryNodePort(10016)
            p2pPort(10017)
            rpcPort(10018)
            notaryClusterAddresses(clusterAddress)
        }
    }

    override fun setup(context: CordformContext) {
        ServiceIdentityGenerator.generateToDisk(notaryNames.map(CordaX500Name::toString).map(context::baseDirectory), advertisedService.type.id, clusterName)
    }
}
