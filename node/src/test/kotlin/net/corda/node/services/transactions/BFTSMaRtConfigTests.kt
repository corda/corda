/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.transactions.BFTSMaRtConfig.Companion.portIsClaimedFormat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import kotlin.test.assertEquals

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
        fun config(vararg ports: Int) = BFTSMaRtConfig(ports.map { NetworkHostAndPort("localhost", it) }, false, false)
        assertThatThrownBy { config(11000, 11001).use {} }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage(portIsClaimedFormat.format("localhost:11001", setOf("localhost:11000", "localhost:11001")))
        assertThatThrownBy { config(11001, 11000).use {} }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage(portIsClaimedFormat.format("localhost:11001", setOf("localhost:11001", "localhost:11002", "localhost:11000")))
        config(11000, 11002).use {} // Non-overlapping.
    }
}
