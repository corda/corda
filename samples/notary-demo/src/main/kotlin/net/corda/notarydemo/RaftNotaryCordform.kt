package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.utilities.getX500Name
import net.corda.core.internal.div
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.demorun.runNodes
import net.corda.demorun.util.*
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.ALICE
import net.corda.testing.BOB
import org.bouncycastle.asn1.x500.X500Name

fun main(args: Array<String>) = RaftNotaryCordform.runNodes()

internal fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map { getX500Name(CN = "Notary Service $it", O = "R3 Ltd", OU = "corda", L = "Zurich", C = "CH") }

private val notaryNames = createNotaryNames(3)

object RaftNotaryCordform : CordformDefinition("build" / "notary-demo-nodes", notaryNames[0]) {
    private val clusterName = X500Name("CN=Raft,O=R3,OU=corda,L=Zurich,C=CH")
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
        ServiceIdentityGenerator.generateToDisk(notaryNames.map { context.baseDirectory(it) }, advertisedService.type.id, clusterName)
    }
}
