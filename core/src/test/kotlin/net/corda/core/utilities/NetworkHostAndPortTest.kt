package net.corda.core.utilities

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import kotlin.test.assertEquals

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
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.INVALID_PORT_FORMAT.format(it))
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
        assertEquals(NetworkHostAndPort("example.com", 1234), NetworkHostAndPort.parse("example.com:1234"))
        assertEquals(NetworkHostAndPort("example.com", 65535), NetworkHostAndPort.parse("example.com:65535"))
        assertEquals(NetworkHostAndPort("1.2.3.4", 1234), NetworkHostAndPort.parse("1.2.3.4:1234"))
        assertEquals(NetworkHostAndPort("::1", 1234), NetworkHostAndPort.parse("[::1]:1234"))
        assertEquals(NetworkHostAndPort("0:0:0:0:0:0:0:1", 1234), NetworkHostAndPort.parse("[0:0:0:0:0:0:0:1]:1234"))
        listOf("0:0:0:0:0:0:0:1:1234", ":1234", "example.com:-1").forEach {
            assertThatThrownBy {
                NetworkHostAndPort.parse(it)
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.UNPARSEABLE_ADDRESS_FORMAT.format(it))
        }
        listOf("example.com:", "example.com").forEach {
            assertThatThrownBy {
                NetworkHostAndPort.parse(it)
            }.isInstanceOf(IllegalArgumentException::class.java).hasMessage(NetworkHostAndPort.MISSING_PORT_FORMAT.format(it))
        }
    }
}
