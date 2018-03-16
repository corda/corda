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
        val keyCopyOption = ToolOption.KeyCopierOption(tempDir / "srcKeystore.jks", tempDir / "destKeystore.jks", "srctestpass", "desttestpass", "TestKeyAlias", null)

        // Prepare source and destination keystores
        val keyPair = Crypto.generateKeyPair()
        val cert = X509Utilities.createSelfSignedCACertificate(X500Principal("O=Test"), keyPair)

        X509KeyStore.fromFile(keyCopyOption.srcPath, keyCopyOption.srcPass, createNew = true).update {
            setPrivateKey(keyCopyOption.srcAlias, keyPair.private, listOf(cert))
        }
        X509KeyStore.fromFile(keyCopyOption.destPath, keyCopyOption.destPass, createNew = true)

        // Copy private key from src keystore to dest keystore using the tool
        keyCopyOption.copyKeystore()

        // Verify key copied correctly
        val destKeystore = X509KeyStore.fromFile(keyCopyOption.destPath, keyCopyOption.destPass)
        assertEquals(keyPair.private, destKeystore.getPrivateKey(keyCopyOption.srcAlias, keyCopyOption.destPass))
        assertEquals(cert, destKeystore.getCertificate(keyCopyOption.srcAlias))
    }
}