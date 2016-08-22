package com.r3corda.node.driver

import com.google.common.net.HostAndPort
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.parsePublicKeyBase58
import com.r3corda.core.crypto.toBase58String
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.node.internal.Node
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.config.NodeConfigurationFromConfig
import com.r3corda.node.services.messaging.ArtemisMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import joptsimple.OptionSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.util.*

private val log: Logger = LoggerFactory.getLogger(NodeRunner::class.java)

class NodeRunner {
    companion object {
        @JvmStatic fun main(arguments: Array<String>) {
            val cliParams = CliParams.parse(CliParams.parser.parse(*arguments))
            val nodeDirectory = Paths.get(cliParams.baseDirectory)
            createNodeRunDirectory(nodeDirectory)

            with(cliParams) {

                val networkMapNodeInfo =
                        if (networkMapName != null && networkMapPublicKey != null && networkMapAddress != null) {
                            NodeInfo(
                                    address = ArtemisMessagingClient.makeRecipient(networkMapAddress),
                                    identity = Party(
                                            name = networkMapName,
                                            owningKey = networkMapPublicKey
                                    ),
                                    advertisedServices = setOf(NetworkMapService.Type)
                            )
                        } else {
                            null
                        }
                val nodeConfiguration = NodeConfigurationFromConfig(
                        NodeConfiguration.loadConfig(
                                baseDirectoryPath = nodeDirectory,
                                allowMissingConfig = false
                        )
                )

                val node = Node(
                        dir = nodeDirectory,
                        p2pAddr = messagingAddress,
                        webServerAddr = apiAddress,
                        configuration = nodeConfiguration,
                        networkMapAddress = networkMapNodeInfo,
                        advertisedServices = services.toSet()
                )

                log.info("Starting ${nodeConfiguration.myLegalName} with services $services on addresses $messagingAddress and $apiAddress")
                node.start()
                node.run()
            }
        }
    }

    class CliParams (
            val services: Set<ServiceType>,
            val networkMapName: String?,
            val networkMapPublicKey: PublicKey?,
            val networkMapAddress: HostAndPort?,
            val messagingAddress: HostAndPort,
            val apiAddress: HostAndPort,
            val baseDirectory: String
    ) {

        companion object {
            val parser = OptionParser()
            val services =
                    parser.accepts("services").withRequiredArg().ofType(String::class.java)
            val networkMapName =
                    parser.accepts("network-map-name").withOptionalArg().ofType(String::class.java)
            val networkMapPublicKey =
                    parser.accepts("network-map-public-key").withOptionalArg().ofType(String::class.java)
            val networkMapAddress =
                    parser.accepts("network-map-address").withOptionalArg().ofType(String::class.java)
            val messagingAddress =
                    parser.accepts("messaging-address").withRequiredArg().ofType(String::class.java)
            val apiAddress =
                    parser.accepts("api-address").withRequiredArg().ofType(String::class.java)
            val baseDirectory =
                    parser.accepts("base-directory").withRequiredArg().ofType(String::class.java)

            private fun <T> requiredArgument(optionSet: OptionSet, spec: ArgumentAcceptingOptionSpec<T>) =
                    optionSet.valueOf(spec) ?: throw IllegalArgumentException("Must provide $spec")

            fun parse(optionSet: OptionSet): CliParams {
                val services = optionSet.valuesOf(services)
                val networkMapName = optionSet.valueOf(networkMapName)
                val networkMapPublicKey = optionSet.valueOf(networkMapPublicKey)?.run { parsePublicKeyBase58(this) }
                val networkMapAddress = optionSet.valueOf(networkMapAddress)
                val messagingAddress = requiredArgument(optionSet, messagingAddress)
                val apiAddress = requiredArgument(optionSet, apiAddress)
                val baseDirectory = requiredArgument(optionSet, baseDirectory)

                return CliParams(
                        services = services.map { object : ServiceType(it) {} }.toSet(),
                        messagingAddress = HostAndPort.fromString(messagingAddress),
                        apiAddress = HostAndPort.fromString(apiAddress),
                        baseDirectory = baseDirectory,
                        networkMapName = networkMapName,
                        networkMapPublicKey = networkMapPublicKey,
                        networkMapAddress = networkMapAddress?.let { HostAndPort.fromString(it) }
                )
            }
        }

        fun toCliArguments(): List<String> {
            val cliArguments = LinkedList<String>()
            if (services.isNotEmpty()) {
                cliArguments.add("--services")
                cliArguments.addAll(services.map { it.toString() })
            }
            if (networkMapName != null) {
                cliArguments.add("--network-map-name")
                cliArguments.add(networkMapName)
            }
            if (networkMapPublicKey != null) {
                cliArguments.add("--network-map-public-key")
                cliArguments.add(networkMapPublicKey.toBase58String())
            }
            if (networkMapAddress != null) {
                cliArguments.add("--network-map-address")
                cliArguments.add(networkMapAddress.toString())
            }
            cliArguments.add("--messaging-address")
            cliArguments.add(messagingAddress.toString())
            cliArguments.add("--api-address")
            cliArguments.add(apiAddress.toString())
            cliArguments.add("--base-directory")
            cliArguments.add(baseDirectory.toString())
            return cliArguments
        }
    }
}

fun createNodeRunDirectory(directory: Path) = directory.toFile().mkdirs()

