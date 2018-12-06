package net.corda.core.node

import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.days
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetNotaryConfig
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeConfigOverrides
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFails

class NetworkParametersTest {
    private val mockNet = InternalMockNetwork(
            defaultParameters = MockNetworkParameters(networkSendManuallyPumped = true),
            notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)))

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

    // Notaries tests
    @Test
    fun `choosing notary not specified in network parameters will fail`() {
        val fakeNotary = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME,
                configOverrides = MockNodeConfigOverrides(notary = MockNetNotaryConfig(validating = false))))
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

    @Test
    fun `auto acceptance checks are correct`() {
        val packageOwnership = mapOf(
                "com.example1" to generateKeyPair().public,
                "com.example2" to generateKeyPair().public)
        val whitelistedContractImplementations = mapOf(
                "example1" to listOf(AttachmentId.randomSHA256()),
                "example2" to listOf(AttachmentId.randomSHA256()))

        val netParams = testNetworkParameters()
        val netParamsAutoAcceptable = netParams.copy(
                packageOwnership = packageOwnership,
                whitelistedContractImplementations = whitelistedContractImplementations)
        val netParamsNotAutoAcceptable = netParamsAutoAcceptable.copy(
                maxMessageSize = netParams.maxMessageSize + 1)

        assert(netParams.canAutoAccept(netParams, emptySet())) {
            "auto-acceptable if identical"
        }
        assert(netParams.canAutoAccept(netParams, NetworkParameters.autoAcceptablePropertyNames)) {
            "auto acceptable if identical regardless of exclusions"
        }
        assert(netParams.canAutoAccept(netParamsAutoAcceptable, emptySet())) {
            "auto-acceptable if only AutoAcceptable params have changed"
        }
        assert(netParams.canAutoAccept(netParamsAutoAcceptable, setOf("modifiedTime"))) {
            "auto-acceptable if only AutoAcceptable params have changed and excluded param has not changed"
        }
        assert(!netParams.canAutoAccept(netParamsNotAutoAcceptable, emptySet())) {
            "not auto-acceptable if non-AutoAcceptable param has changed"
        }
        assert(!netParams.canAutoAccept(netParamsAutoAcceptable, setOf("whitelistedContractImplementations"))) {
            "not auto-acceptable if only AutoAcceptable params have changed but one has been added to the exclusion set"
        }
    }

    // Helpers
    private fun dropParametersToDir(dir: Path, params: NetworkParameters) {
        NetworkParametersCopier(params).install(dir)
    }
}