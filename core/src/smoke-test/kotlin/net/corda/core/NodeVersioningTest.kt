/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import net.corda.testing.common.internal.ProjectStructure
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import kotlin.streams.toList

class NodeVersioningTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)

        val expectedPlatformVersion = (ProjectStructure.projectRootDir / "constants.properties").read {
            val constants = Properties()
            constants.load(it)
            constants.getProperty("platformVersion").toInt()
        }
    }

    private val factory = NodeProcess.Factory()

    private val aliceConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Alice Corp", locality = "Madrid", country = "ES"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = false,
            users = listOf(user)
    )

    @Test
    fun `platform version in manifest file`() {
        val manifest = JarFile(factory.cordaJar.toFile()).manifest
        assertThat(manifest.mainAttributes.getValue("Corda-Platform-Version").toInt()).isEqualTo(expectedPlatformVersion)
    }

    @Test
    fun `platform version from RPC`() {
        val cordappsDir = (factory.baseDirectory(aliceConfig) / NodeProcess.CORDAPPS_DIR_NAME).createDirectories()
        // Find the jar file for the smoke tests of this module
        val selfCordapp = Paths.get("build", "libs").list {
            it.filter { "-smokeTests" in it.toString() }.toList().single()
        }
        selfCordapp.copyToDirectory(cordappsDir)

        factory.create(aliceConfig).use { alice ->
            alice.connect().use {
                val rpc = it.proxy
                assertThat(rpc.protocolVersion).isEqualTo(expectedPlatformVersion)
                assertThat(rpc.nodeInfo().platformVersion).isEqualTo(expectedPlatformVersion)
                assertThat(rpc.startFlow(NodeVersioningTest::GetPlatformVersionFlow).returnValue.getOrThrow()).isEqualTo(expectedPlatformVersion)
            }
        }
    }

    @StartableByRPC
    class GetPlatformVersionFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int = serviceHub.myInfo.platformVersion
    }
}
