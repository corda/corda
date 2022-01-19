package net.corda.node.services.network

import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.testing.common.internal.addNotary
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
import java.net.URL
import java.time.Instant

class NetworkMapTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 1.seconds
    private val portAllocation = incrementalPortAllocation()

    private lateinit var networkMapServer: NetworkMapServer
    private lateinit var compatibilityZone: CompatibilityZoneParams

    @Before
    fun start() {
        networkMapServer = NetworkMapServer(cacheTimeout, portAllocation.nextHostAndPort())
        val address = networkMapServer.start()
        compatibilityZone = SplitCompatibilityZoneParams(
                doormanURL = URL("https://example.org/does/not/exist"),
                networkMapURL = URL("http://$address"),
                pnm = null,
                publishNotaries = {
                    networkMapServer.networkParameters = testNetworkParameters(it, modifiedTime = Instant.ofEpochMilli(random63BitValue()), epoch = 2)
                }
        )
    }

    @After
    fun cleanUp() {
        networkMapServer.close()
    }

    @Test(timeout = 300_000)
    fun `parameters update test`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                allowHibernateToManageAppSchema = false
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

    @Test(timeout = 300_000)
    fun `Can hotload parameters if the notary changes`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                allowHibernateToManageAppSchema = false
        ) {

            val notary: Party = TestIdentity.fresh("test notary").party
            val oldParams = networkMapServer.networkParameters
            val paramsWithNewNotary = oldParams.copy(
                    epoch = 3,
                    modifiedTime = Instant.ofEpochMilli(random63BitValue())).addNotary(notary)

            startNodeAndRunFlagDay(paramsWithNewNotary).use { alice ->
                eventually { assertEquals(paramsWithNewNotary, alice.rpc.networkParameters) }
            }

        }
    }

    @Test(timeout = 300_000)
    fun `If only the notary changes but parameters were not accepted, the node will still shut down on the flag day`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                allowHibernateToManageAppSchema = false
        ) {

            val notary: Party = TestIdentity.fresh("test notary").party
            val oldParams = networkMapServer.networkParameters
            val paramsWithNewNotary = oldParams.copy(
                    epoch = 3,
                    modifiedTime = Instant.ofEpochMilli(random63BitValue())).addNotary(notary)

            val alice = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow() as NodeHandleInternal
            networkMapServer.scheduleParametersUpdate(paramsWithNewNotary, "Next parameters", Instant.ofEpochMilli(random63BitValue()))
            // Wait for network map client to poll for the next update.
            Thread.sleep(cacheTimeout.toMillis() * 2)
            networkMapServer.advertiseNewParameters()
            eventually { assertThatThrownBy { alice.rpc.networkParameters }.hasMessageContaining("Connection failure detected") }

        }
    }

    @Test(timeout = 300_000)
    fun `Can not hotload parameters if non-hotloadable parameter changes and the node will shut down`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                allowHibernateToManageAppSchema = false
        ) {

            val oldParams = networkMapServer.networkParameters
            val paramsWithUpdatedMaxMessageSize = oldParams.copy(
                    epoch = 3,
                    modifiedTime = Instant.ofEpochMilli(random63BitValue()),
                    maxMessageSize = oldParams.maxMessageSize + 1)
            startNodeAndRunFlagDay(paramsWithUpdatedMaxMessageSize).use { alice ->
                eventually { assertThatThrownBy { alice.rpc.networkParameters }.hasMessageContaining("Connection failure detected") }
            }
        }
    }

    private fun DriverDSLImpl.startNodeAndRunFlagDay(newParams: NetworkParameters): NodeHandleInternal {

        val alice = startNode(providedName = ALICE_NAME, devMode = false).getOrThrow() as NodeHandleInternal
        val nextHash = newParams.serialize().hash

        networkMapServer.scheduleParametersUpdate(newParams, "Next parameters", Instant.ofEpochMilli(random63BitValue()))
        // Wait for network map client to poll for the next update.
        Thread.sleep(cacheTimeout.toMillis() * 2)
        alice.rpc.acceptNewNetworkParameters(nextHash)
        assertEquals(nextHash, networkMapServer.latestParametersAccepted(alice.nodeInfo.legalIdentities.first().owningKey))
        assertEquals(networkMapServer.networkParameters, alice.rpc.networkParameters)
        networkMapServer.advertiseNewParameters()
        return alice
    }

    @Test(timeout = 300_000)
    fun `nodes process additions and removals from the network map correctly (and also download the network parameters)`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                allowHibernateToManageAppSchema = false
        ) {
            startNode(providedName = ALICE_NAME, devMode = false).getOrThrow().use { aliceNode ->
                assertDownloadedNetworkParameters(aliceNode)
                aliceNode.onlySees(aliceNode.nodeInfo)

                // Wait for network map client to poll for the next update.
                Thread.sleep(cacheTimeout.toMillis() * 2)

                startNode(providedName = BOB_NAME, devMode = false).getOrThrow().use { bobNode ->
                    bobNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)
                    aliceNode.onlySees(aliceNode.nodeInfo, bobNode.nodeInfo)

                    networkMapServer.removeNodeInfo(aliceNode.nodeInfo)

                    // Wait for network map client to poll for the next update.
                    Thread.sleep(cacheTimeout.toMillis() * 2)

                    bobNode.onlySees(bobNode.nodeInfo)
                }
            }
        }
    }

    @Test(timeout = 300_000)
    fun `test node heartbeat`() {
        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                systemProperties = mapOf("net.corda.node.internal.nodeinfo.publish.interval" to 1.seconds.toString()),
                allowHibernateToManageAppSchema = false
        ) {
            startNode(providedName = ALICE_NAME, devMode = false).getOrThrow().use { aliceNode ->
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
            assertThat(nodeInfosDir.list().single().readObject<SignedNodeInfo>()
                    .verified().legalIdentities.first(), `is`(this.nodeInfo.legalIdentities.first()))
        }
        assertThat(rpc.networkMapSnapshot()).containsOnly(*nodes)
    }
}
