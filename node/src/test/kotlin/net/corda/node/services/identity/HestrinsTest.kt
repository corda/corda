package net.corda.node.services.identity

import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.core.internal.hash
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.lang.IllegalStateException
import java.security.PublicKey
import java.security.SignatureException
import java.util.function.BiConsumer

class HestrinsTest {
    companion object {
        private val alice = Party(ALICE_NAME, generateKeyPair().public)
        private val bob = Party(BOB_NAME, generateKeyPair().public)
    }

    @Rule
    @JvmField
    val exception = ExpectedException.none()

    private lateinit var database: CordaPersistence
    private lateinit var identityService: PersistentIdentityService

    @Before
    fun setup() {
        identityService = PersistentIdentityService(TestingNamedCacheFactory())
        database = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                identityService::wellKnownPartyFromX500Name,
                identityService::wellKnownPartyFromAnonymous
        )
        identityService.database = database
        identityService.ourNames = setOf(ALICE_NAME)
        identityService.start(DEV_ROOT_CA.certificate)
    }

    @After
    fun shutdown() {
        database.close()
    }

    @Test
    fun `look up mapping`() {
        val network = MockNetwork(
                cordappPackages = listOf(),
                threadPerNode = true,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        val partyA = network.createNode()

        val key0 =  createSignedKeyAndVerify(partyA)
        identityService.registerIdentityMapping(alice, key0)

        val key1 =  createSignedKeyAndVerify(partyA)
        identityService.registerIdentityMapping(alice, key1)

        val key2 =  createSignedKeyAndVerify(partyA)
        identityService.registerIdentityMapping(bob, key2)

        exception.expect(IllegalStateException::class.java)
        exception.expectMessage("The public key ${key1.hash} is already assigned to a party.")
        identityService.registerIdentityMapping(bob, key1)
    }

    private fun createSignedKeyAndVerify(node: StartedMockNode) : PublicKey {
        // Generate
        val signedPK = createSignedPublicKey(node.services, UniqueIdentifier().id)
        // Verify signature
        try {
            signedPK.signature.verify(signedPK.publicKeyToPartyMap.serialize().hash.bytes)
        } catch (ex: SignatureException) {
            println("uh oh")
        }
        return signedPK.publicKeyToPartyMap.filter { it.value == node.services.myInfo.singleIdentity() }.keys.first()
    }
}