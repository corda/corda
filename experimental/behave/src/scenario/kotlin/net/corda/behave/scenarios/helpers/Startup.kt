package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState

class Startup(state: ScenarioState) : Substeps(state) {

    fun hasLoggingInformation(nodeName: String) {
        withNetwork {
            log.info("Retrieving logging information for node '$nodeName' ...")
            if (!node(nodeName).nodeInfoGenerationOutput.find("Logs can be found in.*").any()) {
                fail("Unable to find logging information for node $nodeName")
            }

            withClient(nodeName) {
                log.info("$nodeName: ${it.nodeInfo()} has registered flows:")
                for (flow in it.registeredFlows()) {
                    log.info(flow)
                }
            }
        }
    }

    fun hasDatabaseDetails(nodeName: String) {
        withNetwork {
            log.info("Retrieving database details for node '$nodeName' ...")
            if (!node(nodeName).nodeInfoGenerationOutput.find("Database connection url is.*").any()) {
                fail("Unable to find database details for node $nodeName")
            }
        }
    }

    fun hasIdentityDetails(nodeName: String) {
        withNetwork {
            log.info("Retrieving identity details for node '$nodeName' ...")
            try {
                val nodeInfo = node(nodeName).http { it.nodeInfo() }
//                val nodeInfo = node(nodeName).rpc { it.nodeInfo() }
                log.info("\nNode $nodeName identity details: $nodeInfo\n")
            } catch (ex: Exception) {
                log.warn("Failed to retrieve node identity details", ex)
                throw ex
            }
        }
    }

    fun hasPlatformVersion(nodeName: String, platformVersion: Int) {
        withNetwork {
            log.info("Finding platform version for node '$nodeName' ...")
            val logOutput = node(nodeName).logOutput
            if (!logOutput.find(".*Platform Version: $platformVersion .*").any()) {
                val match = logOutput.find(".*Platform Version: .*").firstOrNull()
                if (match == null) {
                    fail("Unable to find platform version for node '$nodeName'")
                } else {
                    val foundVersion = Regex("Platform Version: (\\d+) ")
                            .find(match.contents)
                            ?.groups?.last()?.value
                    fail("Expected platform version $platformVersion for node '$nodeName', " +
                            "but found version $foundVersion")

                }
            }
        }
    }

    fun hasVersion(nodeName: String, version: String) {
        withNetwork {
            log.info("Finding version for node '$nodeName' ...")
            val logOutput = node(nodeName).logOutput
            if (!logOutput.find(".*Release: $version .*").any()) {
                val match = logOutput.find(".*Release: .*").firstOrNull()
                if (match == null) {
                    fail("Unable to find version for node '$nodeName'")
                } else {
                    val foundVersion = Regex("Version: ([^ ]+) ")
                            .find(match.contents)
                            ?.groups?.last()?.value
                    fail("Expected version $version for node '$nodeName', " +
                            "but found version $foundVersion")

                }
            }
        }
    }

    fun hasLoadedCordapp(nodeName: String, cordappName: String) {
        withNetwork {
            log.info("Checking CorDapp $cordappName is loaded in node $nodeName ...\n")
            val logOutput = node(nodeName).logOutput
            if (!logOutput.find(".*Loaded CorDapps.*$cordappName.*").any()) {
                fail("Unable to find $cordappName loaded in node $nodeName")
            }
        }
    }
}