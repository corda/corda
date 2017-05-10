package net.corda.core.identity

import net.corda.contracts.asset.Obligation
import net.corda.core.contracts.GBP
import net.corda.core.contracts.Issued
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.nonEmptySetOf
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.BOC
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

/**
 * Created by rossnicoll on 09/05/2017.
 */
class AnonymousPartySpiderTest {
    @Test
    fun `extracts a single anonymous party`() {
        val expected = AnonymousParty(ALICE_PUBKEY)
        val acceptableContracts = nonEmptySetOf(SecureHash.randomSHA256() as SecureHash)
        val acceptablePayments = nonEmptySetOf(Issued<Currency>(BOC.ref(1), GBP))
        val terms = Obligation.Terms<Currency>(acceptableContracts, acceptablePayments, Instant.now())
        val state = Obligation.State(Obligation.Lifecycle.NORMAL, expected, terms, 1000, BOC.owningKey)
        val spider = AnonymousPartySpider()
        val actual = spider.extractParties(state).singleOrNull()
        assertEquals(expected, actual)
    }
}