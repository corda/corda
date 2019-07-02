package net.corda.node.services.network

import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.*
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.testing.common.internal.eventually
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.internal.*
import net.corda.testing.node.internal.network.NetworkMapServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.URL
import java.time.Instant

@RunWith(Parameterized::class)
class NetworkMapTest(var initFunc: (URL, NetworkMapServer) -> CompatibilityZoneParams) {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 1.seconds
    private val portAllocation = incrementalPortAllocation()

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun runParams() = listOf(
                {
                    addr: URL,
                    nms: NetworkMapServer -> SharedCompatibilityZoneParams(
                            addr,
                            pnm = null,
                            publishNotaries = {
                                nms.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                            }
                    )
                },
                {
                    addr: URL,
                    nms: NetworkMapServer -> SplitCompatibilityZoneParams (
                            doormanURL = URL("http://I/Don't/Exist"),
                            networkMapURL = addr,
                            pnm = null,
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
                notarySpecs = emptyList()
        ) {
            val alice = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow() as NodeHandleInternal
            val nextParams = networkMapServer.networkParameters.copy(
                    epoch = 3,
                    modifiedTime = Instant.ofEpochMilli(random63BitValue()),
                    maxMessageSize = networkMapServer.networkParameters.maxMessageSize + 1)
            val nextHash = nextParams.serialize().hash
            val snapshot = alice.rpc.networkParametersFeed().snapshot
            val updates = alice.rpc.networkParametersFeed().updates.bufferUntilSubscribed()
            assertEquals(null, snapshot)
            assertThat(updates.isEmpty)
            networkMapServer.scheduleParametersUpdate(nextParams, "Next parameters", Instant.ofEpochMilli(random63BitValue()))
            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)
            val laterParams = networkMapServer.networkParameters.copy(
                    epoch = 4,
                    modifiedTime = Instant.ofEpochMilli(random63BitValue()),
                    maxMessageSize = nextParams.maxMessageSize + 1
            )
            val laterHash = laterParams.serialize().hash
            networkMapServer.scheduleParametersUpdate(laterParams, "Another update", Instant.ofEpochMilli(random63BitValue()))
            eventually {
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
    fun `nodes process additions and removals from the network map correctly (and also download the network parameters)`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList()
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow()
            assertDownloadedNetworkParameters(aliceNode)
            aliceNode.onlySees(aliceNode.nodeInfo)

            val bobNode = startNode(providedName = BOB_NAME, devMode = false).getOrThrow()

            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)

            bobNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
            aliceNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)

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
                notarySpecs = emptyList(),
                systemProperties = mapOf("net.corda.node.internal.nodeinfo.publish.interval" to 1.seconds.toString())
        ) {
            val aliceNode = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow()
            val aliceNodeInfo = aliceNode.nodeInfo.serialize().hash
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNodeInfo)
            networkMapServer.removeNodeInfo(aliceNode.nodeInfo)

            var maxRemoveRetries = 5

            // Try to remove multiple times in case the network map republishes just in between the removal and the check.
            while (aliceNodeInfo in networkMapServer.networkMapHashes()) {
                networkMapServer.removeNodeInfo(aliceNode.nodeInfo)
                if (maxRemoveRetries-- == 0) {
                    throw AssertionError("Could not remove Node info.")
                }
            }

            // Wait until the node info is republished.
            Thread.sleep(2000)
            assertThat(networkMapServer.networkMapHashes()).contains(aliceNodeInfo)
        }
    }

    private fun assertDownloadedNetworkParameters(node: NodeHandle) {
        val networkParameters = (node.baseDirectory / NETWORK_PARAMS_FILE_NAME)
                .readObject<SignedNetworkParameters>()
                .verified()
        // We use a random modified time above to make the network parameters unqiue so that we're sure they came
        // from the server
        assertEquals(networkMapServer.networkParameters, networkParameters)
    }

    private fun NodeHandle.onlySees(vararg nodes: NodeInfo) {
        // Make sure the nodes aren't getting the node infos from their additional-node-infos directories
        val nodeInfosDir = baseDirectory / NODE_INFO_DIRECTORY
        if (nodeInfosDir.exists()) {
            assertThat(nodeInfosDir.list().size, `is`(1))
            assertThat(nodeInfosDir.list().single().readObject<SignedNodeInfo>().verified().legalIdentities.first(), `is`( this.nodeInfo.legalIdentities.first()))
        }
        assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
    }
}
