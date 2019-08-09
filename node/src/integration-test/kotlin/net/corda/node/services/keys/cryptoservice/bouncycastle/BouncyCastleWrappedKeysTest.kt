package net.corda.node.services.keys.cryptoservice.bouncycastle

import net.corda.node.services.keys.cryptoservice.AbstractWrappedKeysTest
import net.corda.node.services.keys.cryptoservice.aliceName
import net.corda.node.services.keys.cryptoservice.genevieveName
import net.corda.node.services.keys.cryptoservice.notaryName
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.testing.internal.IntegrationTestSchemas
import org.junit.ClassRule
import java.nio.file.Path

class BouncyCastleWrappedKeysTest: AbstractWrappedKeysTest() {

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, aliceName, genevieveName)
    }

    override fun configPath(): Path? = null

    override fun cryptoServiceName(): String = SupportedCryptoServices.BC_SIMPLE.name

    override fun mode(): String = "DEGRADED_WRAPPED"

    override fun deleteEntries(aliases: List<String>) {
        // no need to do anything, since BCCryptoService is backed by files relevant only for a single test.
    }

}