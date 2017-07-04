package net.corda.core.utilities

import org.junit.Test
import kotlin.test.assertEquals
import org.assertj.core.api.Assertions.assertThatThrownBy

class AuthorityTest {
    /**
     * If a host isn't known-good it should go via the parser, which does some validation.
     */
    @Test
    fun `constructor is not fussy about host`() {
        assertEquals("", Authority("", 1234).host)
        assertEquals("x", Authority("x", 1234).host)
        assertEquals("500", Authority("500", 1234).host)
        assertEquals(" yo  yo\t", Authority(" yo  yo\t", 1234).host)
        assertEquals("[::1]", Authority("[::1]", 1234).host) // Don't do this.
    }

    @Test
    fun `constructor requires a valid port`() {
        assertEquals(0, Authority("example.com", 0).port)
        assertEquals(65535, Authority("example.com", 65535).port)
        listOf(65536, -1).forEach {
            assertThatThrownBy {
                Authority("example.com", it)
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(invalidPortFormat.format(it))
        }
    }

    @Test
    fun `toString works`() {
        assertEquals("example.com:1234", Authority("example.com", 1234).toString())
        assertEquals("example.com:65535", Authority("example.com", 65535).toString())
        assertEquals("1.2.3.4:1234", Authority("1.2.3.4", 1234).toString())
        assertEquals("[::1]:1234", Authority("::1", 1234).toString())
        // Brackets perhaps not necessary in unabbreviated case, but URI seems to need them for parsing:
        assertEquals("[0:0:0:0:0:0:0:1]:1234", Authority("0:0:0:0:0:0:0:1", 1234).toString())
        assertEquals(":1234", Authority("", 1234).toString()) // URI won't parse this.
    }

    @Test
    fun `parseAuthority works`() {
        assertEquals(Authority("example.com", 1234), "example.com:1234".parseAuthority())
        assertEquals(Authority("example.com", 65535), "example.com:65535".parseAuthority())
        assertEquals(Authority("1.2.3.4", 1234), "1.2.3.4:1234".parseAuthority())
        assertEquals(Authority("::1", 1234), "[::1]:1234".parseAuthority())
        assertEquals(Authority("0:0:0:0:0:0:0:1", 1234), "[0:0:0:0:0:0:0:1]:1234".parseAuthority())
        listOf("0:0:0:0:0:0:0:1:1234", ":1234", "example.com:-1").forEach {
            assertThatThrownBy {
                it.parseAuthority()
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(unparseableAddressFormat.format(it))
        }
        listOf("example.com:", "example.com").forEach {
            assertThatThrownBy {
                it.parseAuthority()
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(missingPortFormat.format(it))
        }
    }
}
