package net.corda.demobench.model

import net.corda.core.identity.CordaX500Name
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class NetworkMapConfigTest {
    @Ignore("This has been superseded by validation logic in CordaX500Name")
    @Test
    fun keyValue() {
        val config = NetworkMapConfig(CordaX500Name.parse("O=My\tNasty Little\rLabel\n,L=London,C=GB"), 10000)
        assertEquals("mynastylittlelabel", config.key)
    }

    @Test
    fun removeWhitespace() {
        assertEquals("OneTwoThreeFour!", "One\tTwo   \rThree\r\nFour!".stripWhitespace())
    }

}
