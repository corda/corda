package net.corda.notarydemo

import net.corda.core.crypto.appendToCommonName
import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.demorun.node
import net.corda.demorun.runNodes
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.User
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformContext
import org.bouncycastle.asn1.x500.X500Name

fun main(args: Array<String>) = RaftNotaryCordform.runNodes()

private val notaryNames = (1..3).map { DUMMY_NOTARY.name.appendToCommonName(" $it") }

object RaftNotaryCordform : CordformDefinition("build" / "notary-demo-nodes", notaryNames[0]) {
    private val advertisedNotary = ServiceInfo(RaftValidatingNotaryService.type, X500Name("CN=Raft,O=R3,OU=corda,L=Zurich,C=CH"))

    init {
        node {
            name(ALICE.name.toString())
            nearestCity("London")
            p2pPort(10002)
            rpcPort(10003)
            rpcUsers = listOf(User("demo", "demo", setOf(startFlowPermission<DummyIssueAndMove>(), startFlowPermission<RPCStartableNotaryFlowClient>())).toMap())
        }
        node {
            name(BOB.name.toString())
            nearestCity("New York")
            p2pPort(10005)
            rpcPort(10006)
        }
        node {
            name(notaryNames[0].toString())
            nearestCity("London")
            advertisedServices = listOf(advertisedNotary.toString())
            p2pPort(10009)
            rpcPort(10010)
            notaryNodePort(10008)
        }
        node {
            name(notaryNames[1].toString())
            nearestCity("London")
            advertisedServices = listOf(advertisedNotary.toString())
            p2pPort(10013)
            rpcPort(10014)
            notaryNodePort(10012)
            notaryClusterAddresses = listOf("localhost:10008")
        }
        node {
            name(notaryNames[2].toString())
            nearestCity("London")
            advertisedServices = listOf(advertisedNotary.toString())
            p2pPort(10017)
            rpcPort(10018)
            notaryNodePort(10016)
            notaryClusterAddresses = listOf("localhost:10008")
        }
    }

    override fun setup(context: CordformContext) {
        ServiceIdentityGenerator.generateToDisk(notaryNames.map { context.baseDirectory(it) }, advertisedNotary.type.id, advertisedNotary.name!!)
    }
}
