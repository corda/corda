package net.corda.node.services.identity

import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
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
import org.junit.Test
import java.security.PublicKey
import java.security.SignatureException
import java.util.concurrent.ConcurrentHashMap

class IdentityServiceNoCertificatesTests {
    companion object {
        private val alice = Party(ALICE_NAME, generateKeyPair().public)
        private val bob = Party(BOB_NAME, generateKeyPair().public)
    }

    private lateinit var partyA: StartedMockNode
    private lateinit var identityService: PersistentIdentityService
    private lateinit var network: MockNetwork
    private val keyToParties = ConcurrentHashMap<PublicKey, Party>()
    private val partyToKeys = ConcurrentHashMap<Party, ArrayList<PublicKey>>()


    @Before
    fun setup() {
        network = MockNetwork(
                cordappPackages = listOf(),
                threadPerNode = true,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))

        partyA = network.createNode()
    }

    @After
    fun shutdown() {
        network.stopNodes()
    }


    @Test
    fun `Persistence identity service tests`() {
        identityService = PersistentIdentityService(TestingNamedCacheFactory())
        val database = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                identityService::wellKnownPartyFromX500Name,
                identityService::wellKnownPartyFromAnonymous
        )
        identityService.database = database
        identityService.ourNames = setOf(ALICE_NAME)
        identityService.start(DEV_ROOT_CA.certificate)

        val key =  createSignedKeyAndVerify(partyA)
        val key1 =  createSignedKeyAndVerify(partyA)
        val key2 =  createSignedKeyAndVerify(partyA)


        assertThat(identityService.registerIdentityMapping(alice, key)).isTrue()
        assertThat(identityService.registerIdentityMapping(alice, key1)).isTrue()
        assertThat(identityService.registerIdentityMapping(bob, key1)).isFalse()
        assertThat(identityService.registerIdentityMapping(bob, key2)).isTrue()
        database.close()
    }


    @Test
    fun `In memory identity service tests`() {
        val key =  createSignedKeyAndVerify(partyA)
        val key1 =  createSignedKeyAndVerify(partyA)
        val key2 =  createSignedKeyAndVerify(partyA)

        val inMemoryIdentityService = InMemoryIdentityService(emptyList(), DEV_ROOT_CA.certificate, mapOf(key to alice))

        assertThat(inMemoryIdentityService.registerIdentityMapping(alice, key)).isFalse()
        assertThat(inMemoryIdentityService.registerIdentityMapping(alice, key1)).isTrue()
        assertThat(inMemoryIdentityService.registerIdentityMapping(bob, key1)).isFalse()
        assertThat(inMemoryIdentityService.registerIdentityMapping(bob, key2)).isTrue()
    }


    @Test
    fun `Method testing`() {
        val key =  createSignedKeyAndVerify(partyA)
        val key1 =  createSignedKeyAndVerify(partyA)
        val key2 =  createSignedKeyAndVerify(partyA)
        val key3 =  createSignedKeyAndVerify(partyA)

        val keyToParty = mapOf(key to alice, key1 to alice, key2 to bob)

        // Mimic the copying into ConcurrentHashMap
        keyToParties.putAll(keyToParty)
        val swapped = keyToParty.entries.groupBy { it.value }.mapValues { ArrayList(it.value.map { entries -> entries.key }) }
        partyToKeys.putAll(swapped)

        // Ensure the map was correctly transformed
        assertThat(partyToKeys).hasSize(2)
        assertThat(partyToKeys[alice]).contains(key, key1)
        assertThat(partyToKeys[bob]).contains(key2)

        assertThat(registerIdentityMapping(bob, key1)).isFalse()
        assertThat(registerIdentityMapping(bob, key2)).isFalse()
        assertThat(registerIdentityMapping(alice, key3)).isTrue()
    }


    /**
     *
     */
    private fun registerIdentityMapping(identity: Party, key: PublicKey): Boolean{

        var willRegisterNewMapping = true
        when (keyToParties[key]) {
            null -> {
                keyToParties[key] = identity
            }
            else -> {
                if (identity != keyToParties[key]) {
                    return false
                }
                willRegisterNewMapping = false
            }
        }

        // Check by party
        when (partyToKeys[identity]) {
            null -> {
                partyToKeys.putIfAbsent(identity, arrayListOf(key))
            }
            else -> {
                val keys = partyToKeys[identity]
                if (!keys!!.contains(key)) {
                    keys.add(key)
                    partyToKeys.replace(identity, keys)
                }
            }
        }
        return willRegisterNewMapping
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
