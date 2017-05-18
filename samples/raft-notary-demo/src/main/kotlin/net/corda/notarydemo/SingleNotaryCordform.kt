package net.corda.notarydemo

import net.corda.core.div
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.demorun.node
import net.corda.demorun.runNodes
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformContext

fun main(args: Array<String>) = SingleNotaryCordform.runNodes()

object SingleNotaryCordform : CordformDefinition("build" / "notary-demo-nodes", DUMMY_NOTARY.name) {
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
            name(DUMMY_NOTARY.name.toString())
            nearestCity("London")
            advertisedServices = listOf(ServiceInfo(ValidatingNotaryService.type).toString())
            p2pPort(10009)
            rpcPort(10010)
            notaryNodePort(10008)
        }
    }

    override fun setup(context: CordformContext) {}
}
