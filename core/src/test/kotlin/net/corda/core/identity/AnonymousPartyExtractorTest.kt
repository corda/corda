package net.corda.core.identity

import net.corda.core.utilities.ALICE
import net.corda.testing.ALICE_PUBKEY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnonymousPartyExtractorTest {
    @Test
    fun `extracts an anonymous party`() {
        val expected = AnonymousParty(ALICE_PUBKEY)
        val extractor = AnonymousPartyExtractor()
        val spider = AnonymousPartySpider()
        val actual = extractor.extractParties(spider, expected, mutableListOf()).singleOrNull()
        assertEquals(expected, actual)
    }
}