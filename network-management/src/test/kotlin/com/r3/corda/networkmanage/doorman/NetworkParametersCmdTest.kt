package com.r3.corda.networkmanage.doorman

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.TestIdentity
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class NetworkParametersCmdTest {
    @Test
    fun `maxTransactionSize cannot decrease`() {
        val netParams = testNetworkParameters(maxTransactionSize = 100)
        val netParamsCmd = netParams.toCmd()
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { netParamsCmd.copy(maxTransactionSize = 99).checkCompatibility(netParams) }
                .withMessageContaining("maxTransactionSize")
        netParamsCmd.copy(maxTransactionSize = 100).checkCompatibility(netParams)
        netParamsCmd.copy(maxTransactionSize = 101).checkCompatibility(netParams)
    }

    @Test
    fun `notary cannot be removed`() {
        val alice = NotaryInfo(TestIdentity(ALICE_NAME).party, true)
        val bob = NotaryInfo(TestIdentity(BOB_NAME).party, true)
        val netParams = testNetworkParameters(notaries = listOf(alice, bob))
        val netParamsCmd = netParams.toCmd()
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { netParamsCmd.copy(notaries = listOf(alice)).checkCompatibility(netParams) }
                .withMessageContaining("notaries")
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { netParamsCmd.copy(notaries = emptyList()).checkCompatibility(netParams) }
                .withMessageContaining("notaries")
    }

    @Test
    fun `notary identity key cannot change`() {
        val netParams = testNetworkParameters(notaries = listOf(NotaryInfo(freshParty(ALICE_NAME), true)))
        val notaryKeyChanged = netParams.toCmd().copy(notaries = listOf(NotaryInfo(freshParty(ALICE_NAME), true)))
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { notaryKeyChanged.checkCompatibility(netParams) }
                .withMessageContaining("notaries")
    }

    @Test
    fun `notary name cannot change`() {
        val identity = freshParty(ALICE_NAME)
        val netParams = testNetworkParameters(notaries = listOf(NotaryInfo(identity, true)))
        val notaryNameChanged = netParams.toCmd().copy(notaries = listOf(NotaryInfo(Party(BOB_NAME, identity.owningKey), true)))
        assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { notaryNameChanged.checkCompatibility(netParams) }
                .withMessageContaining("notaries")
    }

    private fun NetworkParameters.toCmd(): NetworkParametersCmd.Set {
        return NetworkParametersCmd.Set(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                parametersUpdate = null
        )
    }

    private fun freshParty(name: CordaX500Name) = TestIdentity(name).party
}