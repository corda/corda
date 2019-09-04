package net.corda.node.services.keys.cryptoservice

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.ManagedCryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore
class HSMStartupTest {

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var mockCryptoService: ManagedCryptoService

    @Before
    fun setUpMockNet() {

        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin(),
                notarySpecs = emptyList()
        )

        mockCryptoService = rigorousMock<ManagedCryptoService>().also {
            doAnswer {
                throw CryptoServiceException("We were late!!!")
            }.whenever(it).containsKey(any())
        }
    }

    private fun createNode(parameters: InternalMockNodeParameters): TestStartedNode {
        return mockNet.createNode(parameters) {
            object : InternalMockNetwork.MockNode(it) {
                override fun makeManagedCryptoService(): ManagedCryptoService {
                    return mockCryptoService
                }
            }
        }
    }

    @Test
    fun `node startup with HSM unavailable`() {
        assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { createNode(InternalMockNodeParameters(legalName = ALICE_NAME, configOverrides = { doReturn(SupportedCryptoServices.BC_SIMPLE).whenever(it).cryptoServiceName })) }
                .withMessage("The cryptoservice configured for legal identity keys (BC_SIMPLE) is unavailable")
                .withCause(CryptoServiceException("We were late!!!"))
    }
}
