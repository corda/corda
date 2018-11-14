package net.corda.bootstrapper.notaries

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.nodes.FoundNode
import java.io.File

class NotaryFinder(private val dirToSearch: File) {

    fun findNotaries(): List<FoundNode> {
        return dirToSearch.walkBottomUp().filter { it.name == "node.conf" && !it.absolutePath.contains(Constants.BOOTSTRAPPER_DIR_NAME) }
                .map {
                    try {
                        ConfigFactory.parseFile(it) to it
                    } catch (e: ConfigException) {
                        null
                    }
                }.filterNotNull()
                .filter { it.first.hasPath("notary") }
                .map { (_, nodeConfigFile) ->
                    FoundNode(nodeConfigFile)
                }.toList()
    }
}

