package net.corda.node.services.keys.cryptoservice.utimaco

import net.corda.core.internal.toPath
import net.corda.node.services.keys.cryptoservice.utimaco.UtimacoCryptoService.Companion.fileBasedAuth
import net.corda.node.services.keys.cryptoservice.utimaco.UtimacoCryptoService.Companion.parseConfigFile
import org.junit.Test
import kotlin.test.assertEquals

class UtimacoCryptoServiceTest {

    @Test
    fun `Parse config file`() {
        val config = parseConfigFile(javaClass.getResource("utimaco_config.yml").toPath())
        assertEquals(true, config.provider.keepSessionAlive)
    }
    @Test
    fun `File based auth`() {
        val auth = fileBasedAuth(javaClass.getResource("uname").toPath(), javaClass.getResource("pw").toPath())
        val credentials = auth()
        assertEquals("testpassword", String(credentials.password))
        assertEquals("testuser", credentials.username)
    }
}