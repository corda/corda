package net.corda.networkbuilder.cli

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.networkbuilder.Constants
import net.corda.networkbuilder.NetworkBuilder
import net.corda.networkbuilder.backends.Backend
import net.corda.networkbuilder.context.Context
import net.corda.networkbuilder.nodes.NodeAdder
import net.corda.networkbuilder.nodes.NodeInstantiator
import net.corda.networkbuilder.toSingleFuture
import java.io.File
import java.util.Locale

class CommandLineInterface {

    fun run(parsedArgs: CliParser) {
        val baseDir = parsedArgs.baseDirectory
        val cacheDir = File(baseDir, Constants.BOOTSTRAPPER_DIR_NAME)
        val networkName = parsedArgs.name ?: "corda-network"
        val objectMapper = Constants.getContextMapper()
        val contextFile = File(cacheDir, "$networkName.yaml")
        if (parsedArgs.isNew()) {
            val (_, context) = NetworkBuilder.instance()
                    .withBasedir(baseDir)
                    .withNetworkName(networkName)
                    .onNodeBuild { builtNode -> println("Built node: ${builtNode.name} to image: ${builtNode.localImageId}") }
                    .onNodePushed { pushedNode -> println("Pushed node: ${pushedNode.name} to: ${pushedNode.remoteImageName}") }
                    .onNodeInstance { instance ->
                        println("Instance of ${instance.name} with id: ${instance.nodeInstanceName} on address: " +
                                "${instance.reachableAddress} {ssh:${instance.portMapping[Constants.NODE_SSHD_PORT]}, " +
                                "p2p:${instance.portMapping[Constants.NODE_P2P_PORT]}}")
                    }
                    .withBackend(parsedArgs.backendType)
                    .withBackendOptions(parsedArgs.backendOptions())
                    .build().getOrThrow()
            persistContext(contextFile, objectMapper, context)
        } else {
            val context = setupContextFromExisting(contextFile, objectMapper)
            val (_, instantiator, _) = Backend.fromContext(context, cacheDir)
            val nodeAdder = NodeAdder(context, NodeInstantiator(instantiator, context))
            parsedArgs.nodesToAdd.map {
                nodeAdder.addNode(context, Constants.ALPHA_NUMERIC_ONLY_REGEX.replace(it.key.lowercase(Locale.getDefault()), ""), CordaX500Name.parse(it.value))
            }.toSingleFuture().getOrThrow()
            persistContext(contextFile, objectMapper, context)
        }
    }

    private fun setupContextFromExisting(contextFile: File, objectMapper: ObjectMapper): Context {
        return contextFile.let { file ->
            if (file.exists()) {
                file.inputStream().use {
                    objectMapper.readValue(it, Context::class.java)
                }
            } else {
                throw IllegalStateException("No existing network context found")
            }
        }
    }

    private fun persistContext(contextFile: File, objectMapper: ObjectMapper, context: Context?) {
        contextFile.outputStream().use {
            objectMapper.writeValue(it, context)
        }
    }
}
