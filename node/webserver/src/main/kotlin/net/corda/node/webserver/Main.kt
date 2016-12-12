package net.corda.node.webserver

import com.google.common.net.HostAndPort
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import java.nio.file.Paths

fun main(args: Array<String>) {
    driver {
        val node = startNode().get()
        WebServer(node.nodeInfo, node.config).start()
    }
}

fun generateNodeConfiguration(): FullNodeConfiguration {
    val messagingAddress = 10002
    val apiAddress = HostAndPort.fromString("localhost:10003")
    val name = "webserver-test"
    val rpcUsers = listOf<User>()

    val baseDirectory = Paths.get("build/webserver")
    val configOverrides = mapOf(
            "myLegalName" to name,
            "basedir" to baseDirectory.normalize().toString(),
            "artemisAddress" to messagingAddress.toString(),
            "webAddress" to apiAddress.toString(),
            "extraAdvertisedServiceIds" to listOf<String>(),
            "networkMapAddress" to "",
            "useTestClock" to false,
            "rpcUsers" to rpcUsers.map {
                mapOf(
                        "user" to it.username,
                        "password" to it.password,
                        "permissions" to it.permissions
                )
            }
    )

    val config = ConfigHelper.loadConfig(
            baseDirectoryPath = baseDirectory,
            allowMissingConfig = true,
            configOverrides = configOverrides
    )

    return FullNodeConfiguration(config)
}