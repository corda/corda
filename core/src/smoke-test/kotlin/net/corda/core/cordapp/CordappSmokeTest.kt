/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import net.corda.smoketesting.NodeProcess.Companion.CORDAPPS_DIR_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

class CordappSmokeTest {
    private companion object {
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15100)
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
    fun `FlowContent appName returns the filename of the CorDapp jar`() {
        val cordappsDir = (factory.baseDirectory(aliceConfig) / CORDAPPS_DIR_NAME).createDirectories()
        // Find the jar file for the smoke tests of this module
        val selfCordapp = Paths.get("build", "libs").list {
            it.filter { "-smokeTests" in it.toString() }.toList().single()
        }
        selfCordapp.copyToDirectory(cordappsDir)

        factory.create(aliceConfig).use { alice ->
            alice.connect().use { connectionToAlice ->
                val aliceIdentity = connectionToAlice.proxy.nodeInfo().legalIdentitiesAndCerts.first().party
                val future = connectionToAlice.proxy.startFlow(::GatherContextsFlow, aliceIdentity).returnValue
                val (sessionInitContext, sessionConfirmContext) = future.getOrThrow()
                val selfCordappName = selfCordapp.fileName.toString().removeSuffix(".jar")
                assertThat(sessionInitContext.appName).isEqualTo(selfCordappName)
                assertThat(sessionConfirmContext.appName).isEqualTo(selfCordappName)
            }
        }
    }

    @Test
    fun `empty cordapps directory`() {
        (factory.baseDirectory(aliceConfig) / CORDAPPS_DIR_NAME).createDirectories()
        factory.create(aliceConfig).close()
    }

    @InitiatingFlow
    @StartableByRPC
    class GatherContextsFlow(private val otherParty: Party) : FlowLogic<Pair<FlowInfo, FlowInfo>>() {
        @Suspendable
        override fun call(): Pair<FlowInfo, FlowInfo> {
            // This receive will kick off SendBackInitiatorFlowContext by sending a session-init with our app name.
            // SendBackInitiatorFlowContext will send back our context using the information from this session-init
            val session = initiateFlow(otherParty)
            val sessionInitContext = session.receive<FlowInfo>().unwrap { it }
            // This context is taken from the session-confirm message
            val sessionConfirmContext = session.getCounterpartyFlowInfo()
            return Pair(sessionInitContext, sessionConfirmContext)
        }
    }

    @Suppress("unused")
    @InitiatedBy(GatherContextsFlow::class)
    class SendBackInitiatorFlowContext(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // An initiated flow calling getFlowInfo on its initiator will get the context from the session-init
            val sessionInitContext = otherPartySession.getCounterpartyFlowInfo()
            otherPartySession.send(sessionInitContext)
        }
    }
}
