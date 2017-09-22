package net.corda.notarydemo

import net.corda.core.internal.div
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.internal.User
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformContext
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.internal.demorun.*

fun main(args: Array<String>) = SingleNotaryCordform.runNodes()

val notaryDemoUser = User("demou", "demop", setOf(startFlowPermission<DummyIssueAndMove>(), startFlowPermission<RPCStartableNotaryFlowClient>()))

object SingleNotaryCordform : CordformDefinition("build" / "notary-demo-nodes", DUMMY_NOTARY.name.toString()) {
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
        node {
            name(DUMMY_NOTARY.name)
            p2pPort(10009)
            rpcPort(10010)
            advertisedServices(ServiceInfo(ValidatingNotaryService.type))
        }
    }

    override fun setup(context: CordformContext) {}
}
