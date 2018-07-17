/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.internal.*
import net.corda.testing.node.internal.network.NetworkMapServer
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.URL
import java.time.Instant

@RunWith(Parameterized::class)
class NetworkMapTest(var initFunc: (URL, NetworkMapServer) -> CompatibilityZoneParams) : IntegrationTest() {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 1.seconds
    private val portAllocation = PortAllocation.Incremental(10000)

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(
                ALICE_NAME.toDatabaseSchemaName(),
                BOB_NAME.toDatabaseSchemaName(),
                DUMMY_NOTARY_NAME.toDatabaseSchemaName())

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun runParams() = listOf(
                { addr: URL, nms: NetworkMapServer ->
                    SharedCompatibilityZoneParams(
                            addr,
                            publishNotaries = {
                                nms.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                            }
                    )
                },
                { addr: URL, nms: NetworkMapServer ->
                    SplitCompatibilityZoneParams(
                            doormanURL = URL("http://I/Don't/Exist"),
                            networkMapURL = addr,
                            publishNotaries = {
                                nms.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                            }
                    )
                }

        )
    }


    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = initFunc(URL("http://$address"), networkMapServer)
    }

    @After
    fun cleanUp() {
        networkMapServer.close()
    }

    @Test
    fun `parameters update test`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val alice = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow() as NodeHandleInternal
            val nextParams = networkMapServer.networkParameters.copy(epoch = 3, modifiedTime = Instant.ofEpochMilli(random63BitValue()))
            val nextHash = nextParams.serialize().hash
            val snapshot = alice.rpc.networkParametersFeed().snapshot
            val updates = alice.rpc.networkParametersFeed().updates.bufferUntilSubscribed()
            assertEquals(null, snapshot)
            assertThat(updates.isEmpty)
            networkMapServer.scheduleParametersUpdate(nextParams, "Next parameters", Instant.ofEpochMilli(random63BitValue()))
            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)
            val laterParams = networkMapServer.networkParameters.copy(epoch = 4, modifiedTime = Instant.ofEpochMilli(random63BitValue()))
            val laterHash = laterParams.serialize().hash
            networkMapServer.scheduleParametersUpdate(laterParams, "Another update", Instant.ofEpochMilli(random63BitValue()))
            Thread.sleep(cacheTimeout.toMillis() * 2)
            updates.expectEvents(isStrict = false) {
                sequence(
                        expect { update: ParametersUpdateInfo ->
                            assertEquals(update.description, "Next parameters")
                            assertEquals(update.hash, nextHash)
                            assertEquals(update.parameters, nextParams)
                        },
                        expect { update: ParametersUpdateInfo ->
                            assertEquals(update.description, "Another update")
                            assertEquals(update.hash, laterHash)
                            assertEquals(update.parameters, laterParams)
                        }
                )
            }
            // This should throw, because the nextHash has been replaced by laterHash
            assertThatThrownBy { alice.rpc.acceptNewNetworkParameters(nextHash) }.hasMessageContaining("Refused to accept parameters with hash")
            alice.rpc.acceptNewNetworkParameters(laterHash)
            assertEquals(laterHash, networkMapServer.latestParametersAccepted(alice.nodeInfo.legalIdentities.first().owningKey))
            networkMapServer.advertiseNewParameters()
            val networkParameters = (alice.configuration.baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME)
                    .readObject<SignedNetworkParameters>().verified()
            assertEquals(networkParameters, laterParams)
        }
    }

    @Test
    fun `node correctly downloads and saves network parameters file on startup`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val alice = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow()
            val networkParameters = (alice.baseDirectory / NETWORK_PARAMS_FILE_NAME)
                    .readObject<SignedNetworkParameters>()
                    .verified()
            // We use a random modified time above to make the network parameters unqiue so that we're sure they came
            // from the server
            assertEquals(networkMapServer.networkParameters, networkParameters)
        }
    }

    @Test
    fun `nodes can see each other using the http network map`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val (aliceNode, bobNode) = listOf(
                    startNode(providedName = ALICE_NAME, devMode = false),
                    startNode(providedName = BOB_NAME, devMode = false)
            ).transpose().getOrThrow()

            aliceNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map add updates correctly when adding new node to network map`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow()

            aliceNode.onlySees(aliceNode.nodeInfo)

            val bobNode = startNode(providedName = BOB_NAME, devMode = false).getOrThrow()

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            bobNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
        }
    }

    @Test
    fun `nodes process network map remove updates correctly`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList()
        ) {
            val (aliceNode, bobNode) = listOf(
                    startNode(providedName = ALICE_NAME, devMode = false),
                    startNode(providedName = BOB_NAME, devMode = false)
            ).transpose().getOrThrow()

            aliceNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
            bobNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)

            networkMapServer.removeNodeInfo(aliceNode.nodeInfo)

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            bobNode.onlySees(bobNode.nodeInfo)
        }
    }

    @Test
    fun `test node heartbeat`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = emptyList(),
                systemProperties = mapOf("net.corda.node.internal.nodeinfo.publish.interval" to 1.seconds.toString())
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow()
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNode.nodeInfo.serialize().hash)
            networkMapServer.removeNodeInfo(aliceNode.nodeInfo)
            assertThat(networkMapServer.networkMapHashes()).doesNotContain(aliceNode.nodeInfo.serialize().hash)
            Thread.sleep(2000)
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNode.nodeInfo.serialize().hash)
        }
    }

    private fun NodeHandle.onlySees(vararg nodes: NodeInfo) {
        // Make sure the nodes aren't getting the node infos from their additional directories
        val nodeInfosDir = baseDirectory / CordformNode.NODE_INFO_DIRECTORY
        if (nodeInfosDir.exists()) {
            assertThat(nodeInfosDir.list()).isEmpty()
        }
        assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
    }
}