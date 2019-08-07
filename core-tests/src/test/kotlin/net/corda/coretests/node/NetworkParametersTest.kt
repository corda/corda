package net.corda.coretests.node

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.generateKeyPair
import net.corda.core.internal.getPackageOwnerOf
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.days
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFails

class NetworkParametersTest {
    private val mockNet = InternalMockNetwork(
            defaultParameters = MockNetworkParameters(networkSendManuallyPumped = true),
            notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME))
    )

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // Minimum Platform Version tests
    @Test
    fun `node shutdowns when on lower platform version than network`() {
        val alice = mockNet.createUnstartedNode(InternalMockNodeParameters(legalName = ALICE_NAME, forcedID = 100, version = MOCK_VERSION_INFO.copy(platformVersion = 1)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(
                notaries = listOf(NotaryInfo(mockNet.defaultNotaryIdentity, true)),
                minimumPlatformVersion = 2)
        dropParametersToDir(aliceDirectory, netParams)
        assertThatThrownBy { alice.start() }.hasMessageContaining("platform version")
    }

    @Test
    fun `node works fine when on higher platform version`() {
        val alice = mockNet.createUnstartedNode(InternalMockNodeParameters(legalName = ALICE_NAME, forcedID = 100, version = MOCK_VERSION_INFO.copy(platformVersion = 2)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(
                notaries = listOf(NotaryInfo(mockNet.defaultNotaryIdentity, true)),
                minimumPlatformVersion = 1)
        dropParametersToDir(aliceDirectory, netParams)
        alice.start()
    }

    @Test
    fun `that we can copy while preserving the event horizon`() {
        // this is defensive tests in response to CORDA-2769
        val aliceNotaryParty = TestIdentity(ALICE_NAME).party
        val aliceNotaryInfo = NotaryInfo(aliceNotaryParty, false)
        val nm1 = NetworkParameters(
                minimumPlatformVersion = 1,
                notaries = listOf(aliceNotaryInfo),
                maxMessageSize = Int.MAX_VALUE,
                maxTransactionSize = Int.MAX_VALUE,
                modifiedTime = Instant.now(),
                epoch = 1,
                whitelistedContractImplementations = mapOf("MyClass" to listOf(AttachmentId.allOnesHash)),
                eventHorizon = Duration.ofDays(1)
        )
        val twoDays = Duration.ofDays(2)
        val nm2 = nm1.copy(minimumPlatformVersion = 2, eventHorizon = twoDays)

        assertEquals(2, nm2.minimumPlatformVersion)
        assertEquals(nm1.notaries, nm2.notaries)
        assertEquals(nm1.maxMessageSize, nm2.maxMessageSize)
        assertEquals(nm1.maxTransactionSize, nm2.maxTransactionSize)
        assertEquals(nm1.modifiedTime, nm2.modifiedTime)
        assertEquals(nm1.epoch, nm2.epoch)
        assertEquals(nm1.whitelistedContractImplementations, nm2.whitelistedContractImplementations)
        assertEquals(twoDays, nm2.eventHorizon)
    }

    // Notaries tests
    @Test
    fun `choosing notary not specified in network parameters will fail`() {
        val fakeNotary = mockNet.createNode(
                InternalMockNodeParameters(
                        legalName = BOB_NAME,
                        configOverrides = {
                            doReturn(NotaryConfig(validating = false)).whenever(it).notary
                        }
                )
        )
        val fakeNotaryId = fakeNotary.info.singleIdentity()
        val alice = mockNet.createPartyNode(ALICE_NAME)
        assertThat(alice.services.networkMapCache.notaryIdentities).doesNotContain(fakeNotaryId)
        assertFails {
            alice.services.startFlow(CashIssueFlow(500.DOLLARS, OpaqueBytes.of(0x01), fakeNotaryId)).resultFuture.getOrThrow()
        }
    }

    @Test
    fun `package ownership checks are correct`() {
        val key1 = generateKeyPair().public
        val key2 = generateKeyPair().public

        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            NetworkParameters(1,
                    emptyList(),
                    2001,
                    2000,
                    Instant.now(),
                    1,
                    emptyMap(),
                    Int.MAX_VALUE.days,
                    mapOf("com.!example.stuff" to key2)
            )
        }.withMessageContaining("Invalid Java package name")

        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            NetworkParameters(1,
                    emptyList(),
                    2001,
                    2000,
                    Instant.now(),
                    1,
                    emptyMap(),
                    Int.MAX_VALUE.days,
                    mapOf(
                            "com.example" to key1,
                            "com.example.stuff" to key2
                    )
            )
        }.withMessage("Multiple packages added to the packageOwnership overlap.")

        val params = NetworkParameters(1,
                emptyList(),
                2001,
                2000,
                Instant.now(),
                1,
                emptyMap(),
                Int.MAX_VALUE.days,
                mapOf(
                        "com.example" to key1,
                        "com.examplestuff" to key2
                )
        )

        assertEquals(params.getPackageOwnerOf("com.example.something.MyClass"), key1)
        assertEquals(params.getPackageOwnerOf("com.examplesomething.MyClass"), null)
        assertEquals(params.getPackageOwnerOf("com.examplestuff.something.MyClass"), key2)
        assertEquals(params.getPackageOwnerOf("com.exam.something.MyClass"), null)
    }

    // Helpers
    private fun dropParametersToDir(dir: Path, params: NetworkParameters) {
        NetworkParametersCopier(params).install(dir)
    }
}
