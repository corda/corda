package net.corda.node.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.JavaPackageName
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.config.NotaryConfig
import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.days
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import java.nio.file.Path
import java.time.Instant
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
        val fakeNotary = mockNet.createNode(InternalMockNodeParameters(legalName = BOB_NAME, configOverrides = {
            val notary = NotaryConfig(false)
            doReturn(notary).whenever(it).notary
        }))
        val fakeNotaryId = fakeNotary.info.singleIdentity()
        val alice = mockNet.createPartyNode(ALICE_NAME)
        assertThat(alice.services.networkMapCache.notaryIdentities).doesNotContain(fakeNotaryId)
        assertFails {
            alice.services.startFlow(CashIssueFlow(500.DOLLARS, OpaqueBytes.of(0x01), fakeNotaryId)).resultFuture.getOrThrow()
        }
    }

    @Test
    fun `maxTransactionSize must be bigger than maxMesssageSize`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            NetworkParameters(1,
                    emptyList(),
                    2000,
                    2001,
                    Instant.now(),
                    1,
                    emptyMap())
        }.withMessage("maxTransactionSize cannot be bigger than maxMessageSize")
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
                    mapOf(
                            JavaPackageName("com.!example.stuff") to key2
                    )
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
                            JavaPackageName("com.example") to key1,
                            JavaPackageName("com.example.stuff") to key2
                    )
            )
        }.withMessage("multiple packages added to the packageOwnership overlap.")

        NetworkParameters(1,
                emptyList(),
                2001,
                2000,
                Instant.now(),
                1,
                emptyMap(),
                Int.MAX_VALUE.days,
                mapOf(
                        JavaPackageName("com.example") to key1,
                        JavaPackageName("com.examplestuff") to key2
                )
        )

        assert(JavaPackageName("com.example").owns("com.example.something.MyClass"))
        assert(!JavaPackageName("com.example").owns("com.examplesomething.MyClass"))
        assert(!JavaPackageName("com.exam").owns("com.example.something.MyClass"))

    }

    // Helpers
    private fun dropParametersToDir(dir: Path, params: NetworkParameters) {
        NetworkParametersCopier(params).install(dir)
    }
}