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

    @Test
    fun `default format should return a sensible display string`() {
        val name = CordaX500Name.parse("CN=cName, OU=orgUnit, O=org, L=city, S=state, C=GB")
        val displayString = name.toDisplayString()
        assertEquals("cName, org, orgUnit, GB", displayString)
    }

    @Test
    fun `default format should handle null name fields`() {
        val name = CordaX500Name.parse("O=org, L=city, C=GB")
        val displayString = name.toDisplayString()
        assertEquals("org, GB", displayString)
    }

    @Test
    fun `custom format should return a sensible display string`() {
        val name = CordaX500Name.parse("CN=cName, OU=orgUnit, O=org, L=city, S=state, C=GB")
        val displayString = name.toDisplayString(CordaX500Name.NameSelector.ORG, CordaX500Name.NameSelector.COUNTRY, CordaX500Name.NameSelector.ORG_UNIT)
        assertEquals("org, GB, orgUnit", displayString)
    }

    @Test
    fun `custom selectors should handle nullable fields`() {
        val name = CordaX500Name.parse("O=org, L=city, C=GB")
        val displayString = name.toDisplayString(CordaX500Name.NameSelector.ORG_UNIT, CordaX500Name.NameSelector.COMMON_NAME, CordaX500Name.NameSelector.COUNTRY)
        assertEquals("GB", displayString)
    }
}
