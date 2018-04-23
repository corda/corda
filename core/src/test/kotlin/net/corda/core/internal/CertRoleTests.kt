/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import org.bouncycastle.asn1.ASN1Integer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CertRoleTests {
    @Test
    fun `should deserialize valid value`() {
        val expected = CertRole.INTERMEDIATE_CA
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