package net.corda.core.utilities

import org.junit.Test
import kotlin.test.assertEquals
import org.assertj.core.api.Assertions.assertThatThrownBy

class NetworkHostAndPortTest {
    /**
     * If a host isn't known-good it should go via the parser, which does some validation.
     */
    @Test
    fun `constructor is not fussy about host`() {
        assertEquals("", NetworkHostAndPort("", 1234).host)
        assertEquals("x", NetworkHostAndPort("x", 1234).host)
        assertEquals("500", NetworkHostAndPort("500", 1234).host)
        assertEquals(" yo  yo\t", NetworkHostAndPort(" yo  yo\t", 1234).host)
        assertEquals("[::1]", NetworkHostAndPort("[::1]", 1234).host) // Don't do this.
    }

    @Test
    fun `constructor requires a valid port`() {
        assertEquals(0, NetworkHostAndPort("example.com", 0).port)
        assertEquals(65535, NetworkHostAndPort("example.com", 65535).port)
        listOf(65536, -1).forEach {
            assertThatThrownBy {
                NetworkHostAndPort("example.com", it)
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.invalidPortFormat.format(it))
        }
    }

    @Test
    fun `toString works`() {
        assertEquals("example.com:1234", NetworkHostAndPort("example.com", 1234).toString())
        assertEquals("example.com:65535", NetworkHostAndPort("example.com", 65535).toString())
        assertEquals("1.2.3.4:1234", NetworkHostAndPort("1.2.3.4", 1234).toString())
        assertEquals("[::1]:1234", NetworkHostAndPort("::1", 1234).toString())
        // Brackets perhaps not necessary in unabbreviated case, but URI seems to need them for parsing:
        assertEquals("[0:0:0:0:0:0:0:1]:1234", NetworkHostAndPort("0:0:0:0:0:0:0:1", 1234).toString())
        assertEquals(":1234", NetworkHostAndPort("", 1234).toString()) // URI won't parse this.
    }

    @Test
    fun `parseNetworkHostAndPort works`() {
        assertEquals(NetworkHostAndPort("example.com", 1234), "example.com:1234".parseNetworkHostAndPort())
        assertEquals(NetworkHostAndPort("example.com", 65535), "example.com:65535".parseNetworkHostAndPort())
        assertEquals(NetworkHostAndPort("1.2.3.4", 1234), "1.2.3.4:1234".parseNetworkHostAndPort())
        assertEquals(NetworkHostAndPort("::1", 1234), "[::1]:1234".parseNetworkHostAndPort())
        assertEquals(NetworkHostAndPort("0:0:0:0:0:0:0:1", 1234), "[0:0:0:0:0:0:0:1]:1234".parseNetworkHostAndPort())
        listOf("0:0:0:0:0:0:0:1:1234", ":1234", "example.com:-1").forEach {
            assertThatThrownBy {
                it.parseNetworkHostAndPort()
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.unparseableAddressFormat.format(it))
        }
        listOf("example.com:", "example.com").forEach {
            assertThatThrownBy {
                it.parseNetworkHostAndPort()
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.missingPortFormat.format(it))
        }
    }
}
