package net.corda.nodeapi.internal.cryptoservice.utimaco

import net.corda.core.internal.toPath
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService.Companion.parseConfigFile
import org.junit.Test
import kotlin.test.assertEquals

class UtimacoCryptoServiceTest {

    @Test
    fun `Parse config file`() {
        val config = parseConfigFile(javaClass.getResource("utimaco.conf").toPath())
        assertEquals(true, config.keepSessionAlive)
    }
}