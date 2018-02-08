package com.r3.corda.networkmanage.dev

import com.r3.corda.networkmanage.hsm.generator.parseCommandLine
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_FILE
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_FILE
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class GeneratorConfigurationTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val configPath = File("dev-generator.conf").absolutePath

    @Test
    fun `config file is parsed correctly`() {
        val config = parseParameters(parseCommandLine("--config-file", configPath).configFile)
        assertEquals(GeneratorConfiguration.DEFAULT_DIRECTORY, config.directory)
        assertEquals(DEV_CA_KEY_STORE_FILE, config.keyStoreFileName)
        assertEquals(DEV_CA_KEY_STORE_PASS, config.keyStorePass)
        assertEquals(DEV_CA_TRUST_STORE_PASS, config.trustStorePass)
        assertEquals(DEV_CA_TRUST_STORE_FILE, config.trustStoreFileName)
    }
}