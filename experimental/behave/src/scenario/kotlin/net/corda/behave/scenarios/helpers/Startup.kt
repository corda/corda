/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState

class Startup(state: ScenarioState) : Substeps(state) {

    fun hasLoggingInformation(nodeName: String) {
        withNetwork {
            log.info("Retrieving logging information for node '$nodeName' ...")
            if (!node(nodeName).nodeInfoGenerationOutput.find("Logs can be found in.*").any()) {
                fail("Unable to find logging information for node $nodeName")
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

}