package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.AliasPrivateKey
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AliasPrivateKeyTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `store AliasPrivateKey entry and cert to keystore`() {
        val alias = "01234567890"
        val aliasPrivateKey = AliasPrivateKey(alias)
        val certificatesDirectory = tempFolder.root.toPath()
        val signingCertStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory, "keystorepass").get(createNew = true)
        signingCertStore.query { setPrivateKey(alias, aliasPrivateKey, listOf(NOT_YET_REGISTERED_MARKER_KEYS_AND_CERTS.ECDSAR1_CERT), "entrypassword") }
        // We can retrieve the certificate.
        assertTrue { signingCertStore.contains(alias) }
        // We can retrieve the certificate.
        assertEquals(NOT_YET_REGISTERED_MARKER_KEYS_AND_CERTS.ECDSAR1_CERT, signingCertStore[alias])
        // Although we can store an AliasPrivateKey, we cannot retrieve it. But, it's fine as we use certStore for storing/handling certs only.
        assertFailsWith<IllegalArgumentException>("Unrecognised algorithm: 2.26.40086077608615255153862931087626791001") {
            signingCertStore.query { getPrivateKey(alias, "entrypassword") }
        }
    }
}
