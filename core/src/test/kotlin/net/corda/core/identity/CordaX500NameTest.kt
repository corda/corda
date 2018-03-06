/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.identity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CordaX500NameTest {
    @Test
    fun `service name with organisational unit`() {
        val name = CordaX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        assertEquals("Service Name", name.commonName)
        assertEquals("Org Unit", name.organisationUnit)
        assertEquals("Bank A", name.organisation)
        assertEquals("New York", name.locality)
        assertEquals(CordaX500Name.parse(name.toString()), name)
        assertEquals(CordaX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `service name`() {
        val name = CordaX500Name.parse("O=Bank A, L=New York, C=US, CN=Service Name")
        assertEquals("Service Name", name.commonName)
        assertNull(name.organisationUnit)
        assertEquals("Bank A", name.organisation)
        assertEquals("New York", name.locality)
        assertEquals(CordaX500Name.parse(name.toString()), name)
        assertEquals(CordaX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `legal entity name`() {
        val name = CordaX500Name.parse("O=Bank A, L=New York, C=US")
        assertNull(name.commonName)
        assertNull(name.organisationUnit)
        assertEquals("Bank A", name.organisation)
        assertEquals("New York", name.locality)
        assertEquals(CordaX500Name.parse(name.toString()), name)
        assertEquals(CordaX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `rejects name with no organisation`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with no locality`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, C=US, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with no country`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, L=New York, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with wrong organisation name format`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=B, L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with unsupported attribute`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, L=New York, C=US, SN=blah")
        }
    }
}
