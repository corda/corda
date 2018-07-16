package net.corda.core.identity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

import java.lang.Character.MIN_VALUE as NULLCHAR

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
    fun `rejects name with unsupported attribute`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bank A, L=New York, C=US, SN=blah")
        }
    }

    @Test
    fun `rejects organisation (but not other attributes) with non-latin letters`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=Bཛྷa, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        // doesn't throw
        checkLocalityAndOrganisationalUnitAndCommonName("Bཛྷa")
    }

    @Test
    fun `organisation (but not other attributes) must have at least two letters`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=B, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        // doesn't throw
        checkLocalityAndOrganisationalUnitAndCommonName("B")
        checkLocalityAndOrganisationalUnitAndCommonName("")
    }

    @Test
    fun `accepts attributes starting with lower case letter`() {
        CordaX500Name.parse("O=bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        checkLocalityAndOrganisationalUnitAndCommonName("bank")
    }

    @Test
    fun `accepts attributes starting with numeric character`() {
            CordaX500Name.parse("O=8Bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name")
            checkLocalityAndOrganisationalUnitAndCommonName("8bank")
    }

    @Test
    fun `accepts attributes with leading whitespace`() {
        CordaX500Name.parse("O= VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        checkLocalityAndOrganisationalUnitAndCommonName(" VALID")
    }

    @Test
    fun `accepts attributes with trailing whitespace`() {
        CordaX500Name.parse("O=VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        checkLocalityAndOrganisationalUnitAndCommonName("VALID ")
    }

    @Test
    fun `rejects attributes with comma`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=IN,VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN,VALID")
    }

    @Test
    fun `accepts org with equals sign`() {
        CordaX500Name.parse("O=IN=VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
    }

    @Test
    fun `accepts organisation with dollar sign`() {
        CordaX500Name.parse("O=VA\$LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        checkLocalityAndOrganisationalUnitAndCommonName("VA\$LID")
    }
    @Test
    fun `rejects attributes with double quotation mark`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=IN\"VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN\"VALID")
    }

    @Test
    fun `accepts organisation with single quotation mark`() {
        CordaX500Name.parse("O=VA'LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        checkLocalityAndOrganisationalUnitAndCommonName("VA'LID")
    }
    @Test
    fun `rejects organisation with backslash`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=IN\\VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN\\VALID")
    }

    @Test
    fun `rejects double spacing only in the organisation attribute`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=IN  VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonName("VA  LID")
    }
    @Test
    fun `rejects organisation (but not other attributes) containing the null character`() {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=IN${NULLCHAR}VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonName("VA${NULLCHAR}LID")
    }

    fun checkLocalityAndOrganisationalUnitAndCommonNameReject(invalid: String) {
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=VALID, L=${invalid}, C=DE, OU=VALID, CN=VALID")
        }
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=VALID, L=VALID, C=DE, OU=${invalid}, CN=VALID")
        }
        assertFailsWith(IllegalArgumentException::class) {
            CordaX500Name.parse("O=VALID, L=VALID, C=DE, OU=VALID, CN=${invalid}")
        }
    }

    fun checkLocalityAndOrganisationalUnitAndCommonName(valid: String) {
        CordaX500Name.parse("O=VALID, L=${valid}, C=DE, OU=VALID, CN=VALID")
        CordaX500Name.parse("O=VALID, L=VALID, C=DE, OU=${valid}, CN=VALID")
        CordaX500Name.parse("O=VALID, L=VALID, C=DE, OU=VALID, CN=${valid}")
    }
}
