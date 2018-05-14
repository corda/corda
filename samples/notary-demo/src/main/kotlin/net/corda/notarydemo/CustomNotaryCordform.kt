/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarydemo

import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.node.internal.demorun.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import java.nio.file.Paths

fun main(args: Array<String>) = CustomNotaryCordform().nodeRunner().deployAndRunNodes()

class CustomNotaryCordform : CordformDefinition() {
    init {
        nodesDirectory = Paths.get("build", "nodes", "nodesCustom")
        node {
            name(ALICE_NAME)
            p2pPort(10002)
            rpcSettings {
                address("localhost:10003")
                adminAddress("localhost:10103")
            }
            rpcUsers(notaryDemoUser)
            devMode(true)
        }
        node {
            name(BOB_NAME)
            p2pPort(10005)
            rpcSettings {
                address("localhost:10006")
                adminAddress("localhost:10106")
            }
            devMode(true)
        }
        node {
            name(DUMMY_NOTARY_NAME)
            p2pPort(10009)
            rpcSettings {
                address("localhost:10010")
                adminAddress("localhost:10110")
            }
            notary(NotaryConfig(validating = true, custom = true))
            devMode(true)
        }
    }

    override fun setup(context: CordformContext) {}
}