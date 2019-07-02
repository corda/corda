package net.corda.networkbuilder.nodes

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.networkbuilder.Constants
import net.corda.core.utilities.contextLogger
import java.io.File

class NodeFinder(private val dirToSearch: File) {

    fun findNodes(): List<FoundNode> = findNodes { config -> !isNotary(config) }

    fun findNotaries(): List<FoundNode> = findNodes { config -> isNotary(config) }

    private fun isNotary(config: Config) = config.hasPath("notary")

    private fun findNodes(filter: (Config) -> Boolean): List<FoundNode> {
        return dirToSearch.walkBottomUp().filter {
            it.name == "node.conf" && !it.absolutePath.contains(Constants.BOOTSTRAPPER_DIR_NAME)
        }.mapNotNull {
            try {
                ConfigFactory.parseFile(it) to it
            } catch (e: ConfigException) {
                null
            }
        }.filter {
            filter(it.first)
        }.map { (_, nodeConfigFile) ->
            LOG.info("We've found a node with name: ${nodeConfigFile.parentFile.name}")
            FoundNode(nodeConfigFile, nodeConfigFile.parentFile)
        }.toList()
    }

    companion object {
        val LOG = contextLogger()
    }
}