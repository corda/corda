package net.corda.demobench.model

import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import kotlin.test.assertEquals

class NetworkMapConfigTest {

    @Test
    fun keyValue() {
        val config = NetworkMapConfig(X500Name("CN=My\tNasty Little\rLabel\n"), 10000)
        assertEquals("mynastylittlelabel", config.key)
    }

    @Test
    fun removeWhitespace() {
        assertEquals("OneTwoThreeFour!", "One\tTwo   \rThree\r\nFour!".stripWhitespace())
    }

}
