package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.config.User
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.internal.demorun.*
import java.nio.file.Paths

fun main(args: Array<String>) = SingleNotaryCordform().deployNodes()

val notaryDemoUser = User("demou", "demop", setOf(all()))

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
class SingleNotaryCordform : CordformDefinition() {
    init {
        nodesDirectory = Paths.get("build", "nodes", "nodesSingle")
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
            notary(NotaryConfig(validating = true))
        }
    }

    override fun setup(context: CordformContext) {}
}
