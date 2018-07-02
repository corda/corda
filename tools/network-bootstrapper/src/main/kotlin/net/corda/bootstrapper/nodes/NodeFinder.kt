package net.corda.bootstrapper.nodes

import com.typesafe.config.ConfigFactory
import net.corda.bootstrapper.Constants
import org.slf4j.LoggerFactory
import java.io.File

class NodeFinder(private val scratchDir: File) {


    fun findNodes(): List<FoundNode> {
        return scratchDir.walkBottomUp().filter { it.name == "node.conf" && !it.absolutePath.contains(Constants.BOOTSTRAPPER_DIR_NAME) }.map {
            try {
                ConfigFactory.parseFile(it) to it
            } catch (t: Throwable) {
                null
            }
        }.filterNotNull()
                .filter { !it.first.hasPath("notary") }
                .map { (nodeConfig, nodeConfigFile) ->
                    LOG.info("We've found a node with name: ${nodeConfigFile.parentFile.name}")
                    FoundNode(nodeConfigFile, nodeConfigFile.parentFile)
                }.toList()

    }

    companion object {
        val LOG = LoggerFactory.getLogger(NodeFinder::class.java)
    }

}

