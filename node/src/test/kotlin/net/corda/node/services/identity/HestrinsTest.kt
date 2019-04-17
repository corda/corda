package net.corda.node.services.identity

import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.MockNetwork
import org.junit.Test
import java.security.SignatureException

class HestrinsTest {

    @Test
    fun `look up mapping`() {
        val identityService = PersistentIdentityService(TestingNamedCacheFactory())
        val network = MockNetwork(
                cordappPackages = listOf(),
                threadPerNode = true,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        val partyA = network.createNode()
        val partyB = network.createNode()

        val alice = Party(ALICE_NAME, generateKeyPair().public)

        // Call function to generate object
        val signedPK = createSignedPublicKey(partyA.services, UniqueIdentifier().id)

        // Verify signature
        try {
            signedPK.signature.verify(signedPK.publicKeyToPartyMap.keys.first().encoded)
        } catch (ex: SignatureException) {
            println("uh oh")
        }

        // Unwrap the key
        val key = signedPK.publicKeyToPartyMap.filter { it.value == partyA.services.myInfo.singleIdentity() }.keys.first()

        // Call this guy
        identityService.registerIdentityMapping(alice, key)
    }
}