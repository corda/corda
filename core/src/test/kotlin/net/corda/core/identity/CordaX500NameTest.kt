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
    fun `should provide the shortest possible unique name given set of nodes and selectors`() {
        val name1 = CordaX500Name.parse("O=blarg,   OU=unit,    C=GB,   L=city, CN=CommonName")
        val name2 = CordaX500Name.parse("O=org,     OU=blarg,   C=GB,   L=city, CN=CommonName")
        val name3 = CordaX500Name.parse("O=org,     OU=unit,    C=BL,   L=city, CN=CommonName")
        val name4 = CordaX500Name.parse("O=org,     OU=unit,    C=US,   L=city, CN=Blaargh")
        val name5 = CordaX500Name.parse("O=org,     OU=unit,    C=US,   L=city, CN=Blargh")
        val outsiderName = CordaX500Name.parse("O=thisIs,  OU=unit,    C=BL,   L=city, CN=Blargh")

        val displayString1 = name1.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        val displayString2 = name2.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        val displayString3 = name3.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        val displayString4 = name4.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        val displayString5 = name5.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        val displayString6 = outsiderName.toUniqueName(setOf(name1, name2, name3, name4, name5),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME)

        assertEquals("blarg", displayString1)
        assertEquals("org, blarg", displayString2)
        assertEquals("org, unit, BL", displayString3)
        assertEquals("org, unit, US, Blaargh", displayString4)
        assertEquals("org, unit, US, Blargh", displayString5)
        assertEquals("thisIs", displayString6)
    }

    @Test(expected = IllegalStateException::class)
    fun `should throw exception if unable to produce unique name with given selectors`() {
        val name1 = CordaX500Name.parse("O=org,   OU=unit,    C=GB,   L=LONDON, CN=CommonName")
        val name2 = CordaX500Name.parse("O=org,     OU=unit,   C=GB,  L=NEW_YOURK, CN=CommonName")

        println(name1.toUniqueName(setOf(name1, name2),
                CordaX500Name.NameSelector.ORG,
                CordaX500Name.NameSelector.ORG_UNIT,
                CordaX500Name.NameSelector.COUNTRY,
                CordaX500Name.NameSelector.COMMON_NAME))

    }
}
