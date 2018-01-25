package net.corda.notarytest

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.node.User
import net.corda.testing.node.internal.demorun.*
import java.nio.file.Paths

fun main(args: Array<String>) = HealthCheckCordform().deployNodes()

val notaryDemoUser = User("demou", "demop", setOf(all()))

// This is not the intended final design for how to use CordformDefinition, please treat this as experimental and DO
// NOT use this as a design to copy.
class HealthCheckCordform : CordformDefinition() {
    init {
        nodesDirectory = Paths.get("build", "nodes")
        node {
            name(CordaX500Name("R3 Notary Health Check", "London", "GB"))
            p2pPort(10002)
            rpcPort(10003)
            rpcUsers(notaryDemoUser)
        }
        node {
            name(CordaX500Name("Notary Service 0", "London", "GB"))
            p2pPort(10009)
            rpcPort(10010)
            notary(NotaryConfig(validating = false))
            extraConfig = mapOf(
                    "mysql" to mapOf(
                            "jdbcUrl" to "jdbc:mysql://notary-10.northeurope.cloudapp.azure.com:3306/corda?rewriteBatchedStatements=true&useSSL=false",
                            "username" to "",
                            "password" to "",
                            "autoCommit" to "false"
                    )
            )
        }
    }

    override fun setup(context: CordformContext) {}
}
