package net.corda.node.services.keys.cryptoservice.bouncycastle

import net.corda.node.services.keys.cryptoservice.AbstractWrappedKeysTest
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import java.nio.file.Path

class BouncyCastleWrappedKeysTest: AbstractWrappedKeysTest() {

    override fun configPath(): Path? = null

    override fun cryptoServiceName(): String = SupportedCryptoServices.BC_SIMPLE.name

    override fun mode(): String = "DEGRADED_WRAPPED"

    override fun deleteEntries(aliases: List<String>) {
        // no need to do anything, since BCCryptoService is backed by files relevant only for a single test.
    }

}