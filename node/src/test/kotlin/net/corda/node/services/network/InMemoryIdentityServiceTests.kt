package net.corda.node.services.network

import net.corda.core.crypto.Party
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.generateKeyPair
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_A_KEY
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_BANK_B_KEY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the in memory identity service.
 */
class InMemoryIdentityServiceTests {

    @Test
    fun `get all identities`() {
        val service = InMemoryIdentityService()
        assertNull(service.getAllIdentities().firstOrNull())
        service.registerIdentity(DUMMY_BANK_A)
        var expected = setOf(DUMMY_BANK_A)
        var actual = service.getAllIdentities().toHashSet()
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        service.registerIdentity(DUMMY_BANK_B)
        expected = setOf(DUMMY_BANK_A, DUMMY_BANK_B)
        actual = service.getAllIdentities().toHashSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `get identity by key`() {
        val service = InMemoryIdentityService()
        assertNull(service.partyFromKey(DUMMY_BANK_A_KEY.public))
        service.registerIdentity(DUMMY_BANK_A)
        assertEquals(DUMMY_BANK_A, service.partyFromKey(DUMMY_BANK_A_KEY.public))
        assertNull(service.partyFromKey(DUMMY_BANK_B_KEY.public))
    }

    @Test
    fun `get identity by name with no registered identities`() {
        val service = InMemoryIdentityService()
        assertNull(service.partyFromName(DUMMY_BANK_A.name))
    }

    @Test
    fun `get identity by name`() {
        val service = InMemoryIdentityService()
        val identities = listOf("Node A", "Node B", "Node C")
                .map { Party("CN=$it,O=R3,OU=corda,L=London,C=UK", generateKeyPair().public) }
        assertNull(service.partyFromName(identities.first().name))
        identities.forEach { service.registerIdentity(it) }
        identities.forEach { assertEquals(it, service.partyFromName(it.name)) }
    }
}
