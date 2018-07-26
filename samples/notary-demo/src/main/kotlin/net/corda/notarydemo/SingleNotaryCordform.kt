package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.User
import net.corda.testing.node.internal.demorun.*
import java.nio.file.Paths

fun main(args: Array<String>) = SingleNotaryCordform().nodeRunner().deployAndRunNodes()

val notaryDemoUser = User("demou", "demop", setOf(all()))

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
class SingleNotaryCordform : CordformDefinition() {
    init {
        nodesDirectory = Paths.get("build", "nodes", "nodesSingle")
        node {
            name(ALICE_NAME)
            p2pPort(10002)
            rpcSettings {
                address("localhost:10003")
                adminAddress("localhost:10103")
            }
            rpcUsers(notaryDemoUser)
            devMode(true)
            extraConfig = mapOf("h2Settings" to mapOf("address" to "localhost:0"))
        }
        node {
            name(BOB_NAME)
            p2pPort(10005)
            rpcSettings {
                address("localhost:10006")
                adminAddress("localhost:10106")
            }
            devMode(true)
            extraConfig = mapOf("h2Settings" to mapOf("address" to "localhost:0"))
        }
        node {
            name(DUMMY_NOTARY_NAME)
            p2pPort(10009)
            rpcSettings {
                address("localhost:10010")
                adminAddress("localhost:10110")
            }
            notary(NotaryConfig(validating = true))
            devMode(true)
            extraConfig = mapOf("h2Settings" to mapOf("address" to "localhost:0"))
        }
    }

    override fun setup(context: CordformContext) {}
}
