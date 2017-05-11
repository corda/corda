package net.corda.core.identity

import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PartyTest {
    @Test
    fun `equality between full and full parties`() {
        val keyA = entropyToKeyPair(BigInteger.valueOf(20170207L)).public
        val keyB = entropyToKeyPair(BigInteger.valueOf(7201702L)).public
        val partyWithKeyA = Party(ALICE.name, keyA)
        val partyWithKeyB = Party(ALICE.name, keyB)
        val differentParty = Party(BOB.name, keyA)
        assertEquals<AbstractParty>(partyWithKeyA, partyWithKeyB)
        assertNotEquals<AbstractParty>(partyWithKeyA, differentParty)
        assertNotEquals<AbstractParty>(partyWithKeyB, differentParty)
    }

    @Test
    fun `equality between anonymous and full parties`() {
        val key = entropyToKeyPair(BigInteger.valueOf(20170207L)).public
        val differentKey = entropyToKeyPair(BigInteger.valueOf(7201702L)).public
        val anonymousParty = AnonymousParty(key)
        val party = Party(ALICE.name, key)
        assertEquals<AbstractParty>(party, anonymousParty)
        assertEquals<AbstractParty>(anonymousParty, party)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), anonymousParty)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), party)
    }

    @Test
    fun `equality between anonymous parties`() {
        val key = entropyToKeyPair(BigInteger.valueOf(20170207L)).public
        val differentKey = entropyToKeyPair(BigInteger.valueOf(7201702L)).public
        val partyA = AnonymousParty(key)
        val partyB = AnonymousParty(key)
        assertEquals<AbstractParty>(partyB, partyA)
        assertEquals<AbstractParty>(partyA, partyB)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), partyA)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), partyB)
    }
}