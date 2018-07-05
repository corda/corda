package net.corda.core.identity

import net.corda.core.internal.toOrderedX500Name
import net.corda.core.internal.x500Name
import org.junit.Test
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals

class X500UtilsTest {

    @Test
    fun `X500Name idempotent to different ordering of the parts`() {
        // given
        val orderingA = "O=Bank A, OU=Organisation Unit, L=New York, C=US"
        val orderingB = "OU=Organisation Unit, O=Bank A, L=New York, C=US"
        val orderingC = "L=New York, O=Bank A, C=US, OU=Organisation Unit"

        // when
        val x500NameA = X500Principal(orderingA).toOrderedX500Name()
        val x500NameB = X500Principal(orderingB).toOrderedX500Name()
        val x500NameC = X500Principal(orderingC).toOrderedX500Name()

        // then
        assertEquals(x500NameA.toString(), x500NameB.toString())
        assertEquals(x500NameB.toString(), x500NameC.toString())
    }

    @Test
    fun `x500Name maintains the order of the parts`() {
        // given
        val orderingA = CordaX500Name.parse("O=Bank A, OU=Organisation Unit, L=New York, C=US")
        val orderingB = CordaX500Name.parse("OU=Organisation Unit, O=Bank A, L=New York, C=US")
        val orderingC = CordaX500Name.parse("L=New York, O=Bank A, C=US, OU=Organisation Unit")

        // when
        val x500NameA = orderingA.x500Name
        val x500NameB = orderingB.x500Name
        val x500NameC = orderingC.x500Name

        // then
        assertEquals(x500NameA.toString(), x500NameB.toString())
        assertEquals(x500NameB.toString(), x500NameC.toString())
    }
}
