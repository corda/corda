package net.corda.node.services.transactions

import com.google.common.net.HostAndPort
import net.corda.node.services.transactions.BFTSMaRtConfig.Companion.portIsClaimedFormat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BFTSMaRtConfigTests {
    @Test
    fun `replica arithmetic`() {
        (1..20).forEach { n ->
            assertEquals(n, maxFaultyReplicas(n) + minCorrectReplicas(n))
        }
        (1..3).forEach { n -> assertEquals(0, maxFaultyReplicas(n)) }
        (4..6).forEach { n -> assertEquals(1, maxFaultyReplicas(n)) }
        (7..9).forEach { n -> assertEquals(2, maxFaultyReplicas(n)) }
        10.let { n -> assertEquals(3, maxFaultyReplicas(n)) }
    }

    @Test
    fun `min cluster size`() {
        assertEquals(1, minClusterSize(0))
        assertEquals(4, minClusterSize(1))
        assertEquals(7, minClusterSize(2))
        assertEquals(10, minClusterSize(3))
    }

    @Test
    fun `overlapping port ranges are rejected`() {
        fun addresses(vararg ports: Int) = ports.map { HostAndPort.fromParts("localhost", it) }
        assertFailsWith(IllegalArgumentException::class, portIsClaimedFormat.format(11001, setOf(11000, 11001))) {
            BFTSMaRtConfig(addresses(11000, 11001)).use {}
        }
        assertFailsWith(IllegalArgumentException::class, portIsClaimedFormat.format(11001, setOf(11001, 11002))) {
            BFTSMaRtConfig(addresses(11001, 11000)).use {}
        }
        BFTSMaRtConfig(addresses(11000, 11002)).use {} // Non-overlapping.
    }
}
