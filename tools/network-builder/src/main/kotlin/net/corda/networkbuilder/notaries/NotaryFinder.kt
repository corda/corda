package net.corda.networkbuilder.notaries

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.networkbuilder.Constants
import net.corda.networkbuilder.nodes.FoundNode
import java.io.File

class NotaryFinder(private val dirToSearch: File) {

    fun findNotaries(): List<FoundNode> {
        return dirToSearch.walkBottomUp().filter {
            it.name == "node.conf" && !it.absolutePath.contains(Constants.BOOTSTRAPPER_DIR_NAME)
        }.mapNotNull {
            try {
                ConfigFactory.parseFile(it) to it
            } catch (e: ConfigException) {
                null
            }
        }.filter {
            it.first.hasPath("notary")
        }.map { (_, nodeConfigFile) ->
            FoundNode(nodeConfigFile)
        }.toList()
    }
}

