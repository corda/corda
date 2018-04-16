package com.r3.corda.networkmanage.registration

import net.corda.core.crypto.Crypto
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import javax.security.auth.x500.X500Principal

class KeyCopyToolTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()
    private val tempDir: Path by lazy { tempFolder.root.toPath() }

    @Test
    fun `key copy correctly`() {
        val keyCopyOption = ToolOption.KeyCopierOption(
                sourceFile = tempDir / "srcKeystore.jks",
                destinationFile = tempDir / "destKeystore.jks",
                sourcePassword = "srctestpass",
                destinationPassword = "desttestpass",
                sourceAlias = "TestKeyAlias",
                destinationAlias = null)

        // Prepare source and destination keystores
        val keyPair = Crypto.generateKeyPair()
        val cert = X509Utilities.createSelfSignedCACertificate(X500Principal("O=Test"), keyPair)

        X509KeyStore.fromFile(keyCopyOption.sourceFile, keyCopyOption.sourcePassword!!, createNew = true).update {
            setPrivateKey(keyCopyOption.sourceAlias, keyPair.private, listOf(cert))
        }
        X509KeyStore.fromFile(keyCopyOption.destinationFile, keyCopyOption.destinationPassword!!, createNew = true)

        // Copy private key from src keystore to dest keystore using the tool
        keyCopyOption.copyKeystore()

        // Verify key copied correctly
        val destKeystore = X509KeyStore.fromFile(keyCopyOption.destinationFile, keyCopyOption.destinationPassword!!)
        assertEquals(keyPair.private, destKeystore.getPrivateKey(keyCopyOption.sourceAlias, keyCopyOption.destinationPassword!!))
        assertEquals(cert, destKeystore.getCertificate(keyCopyOption.sourceAlias))
    }
}