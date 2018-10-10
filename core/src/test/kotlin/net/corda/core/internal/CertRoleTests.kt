package net.corda.core.internal

import org.bouncycastle.asn1.ASN1Integer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CertRoleTests {
    @Test
    fun `should deserialize valid value`() {
        val expected = CertRole.DOORMAN_CA
        val actual = CertRole.getInstance(ASN1Integer(1L))
        assertEquals(expected, actual)
    }

    @Test
    fun `should reject invalid values`() {
        // Below the lowest used value
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(0L)) }
        // Outside of the array size, but a valid integer
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(Integer.MAX_VALUE - 1L)) }
        // Outside of the range of integers
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(Integer.MAX_VALUE + 1L)) }
    }
}