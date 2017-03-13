package net.corda.core.crypto

import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PartyTest {
    @Test
    fun `equality`() {
        val key = entropyToKeyPair(BigInteger.valueOf(20170207L)).public.composite
        val differentKey = entropyToKeyPair(BigInteger.valueOf(7201702L)).public.composite
        val anonymousParty = AnonymousParty(key)
        val party = Party("test key", key)
        assertEquals<AbstractParty>(party, anonymousParty)
        assertEquals<AbstractParty>(anonymousParty, party)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), anonymousParty)
        assertNotEquals<AbstractParty>(AnonymousParty(differentKey), party)
    }
}