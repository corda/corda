package net.corda.node.services

import net.corda.contracts.djvm.crypto.DeterministicCryptoContract.Validate
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.Crypto.DEFAULT_SIGNATURE_SCHEME
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.djvm.crypto.DeterministicCryptoFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.security.KeyPairGenerator

@Suppress("FunctionName")
class DeterministicContractCryptoTest {
    companion object {
        const val MESSAGE = "Very Important Data! Do Not Change!"
        val logger = loggerFor<DeterministicContractCryptoTest>()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun parametersFor(djvmSources: DeterministicSourcesRule): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(
                    cordappWithPackages("net.corda.flows.djvm.crypto"),
                    CustomCordapp(
                        packages = setOf("net.corda.contracts.djvm.crypto"),
                        name = "deterministic-crypto-contract"
                    ).signed()
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test
    fun `test DJVM can verify using crypto`() {
        val keyPair = KeyPairGenerator.getInstance(DEFAULT_SIGNATURE_SCHEME.algorithmName).genKeyPair()
        val importantData = OpaqueBytes(MESSAGE.toByteArray())
        val signature = OpaqueBytes(Crypto.doSign(DEFAULT_SIGNATURE_SCHEME, keyPair.`private`, importantData.bytes))

        val validate = Validate(
            schemeCodeName = DEFAULT_SIGNATURE_SCHEME.schemeCodeName,
            publicKey = keyPair.`public`
        )

        driver(parametersFor(djvmSources)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::DeterministicCryptoFlow, validate, importantData, signature)
                        .returnValue.getOrThrow()
            }
            logger.info("TX-ID: {}", txId)
        }
    }
}
