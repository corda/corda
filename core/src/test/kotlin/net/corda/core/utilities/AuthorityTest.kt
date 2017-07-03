package net.corda.core.utilities

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorityTest {
    @Test
    fun `constructor is not fussy about host`() {
        assertEquals("", Authority("", 1234).host)
        assertEquals("x", Authority("x", 1234).host)
        assertEquals("500", Authority("500", 1234).host)
        assertEquals(" yo  yo\t", Authority(" yo  yo\t", 1234).host)
        assertEquals("[::1]", Authority("[::1]", 1234).host) // Don't do this.
    }

    @Test
    fun `constructor is not fussy about port`() {
        assertEquals(0, Authority("example.com", 0).port)
        assertEquals(-1, Authority("example.com", -1).port) // MockNode needs this.
        assertEquals(999999, Authority("example.com", 999999).port)
    }

    @Test
    fun `toString works`() {
        assertEquals("example.com:1234", Authority("example.com", 1234).toString())
        assertEquals("example.com:999999", Authority("example.com", 999999).toString())
        assertEquals("1.2.3.4:1234", Authority("1.2.3.4", 1234).toString())
        assertEquals("[::1]:1234", Authority("::1", 1234).toString())
        // Brackets perhaps not necessary in unabbreviated case, but URI seems to need them for parsing:
        assertEquals("[0:0:0:0:0:0:0:1]:1234", Authority("0:0:0:0:0:0:0:1", 1234).toString())
        assertEquals(":1234", Authority("", 1234).toString()) // URI won't parse this.
    }

    @Test
    fun `parseAuthority works`() {
        assertEquals(Authority("example.com", 1234), "example.com:1234".parseAuthority())
        assertEquals(Authority("example.com", 999999), "example.com:999999".parseAuthority())
        assertEquals(Authority("1.2.3.4", 1234), "1.2.3.4:1234".parseAuthority())
        assertEquals(Authority("::1", 1234), "[::1]:1234".parseAuthority())
        assertEquals(Authority("0:0:0:0:0:0:0:1", 1234), "[0:0:0:0:0:0:0:1]:1234".parseAuthority())
        assertFailsWith(IllegalArgumentException::class) { "0:0:0:0:0:0:0:1:1234".parseAuthority() }
        assertFailsWith(IllegalArgumentException::class) { ":1234".parseAuthority() }
        assertFailsWith(IllegalArgumentException::class) { "example.com:-1".parseAuthority() }
        assertEquals(Authority("example.com", -1), "example.com".parseAuthority())
    }
}
