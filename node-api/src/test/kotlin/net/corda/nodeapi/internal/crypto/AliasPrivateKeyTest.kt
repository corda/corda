package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
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
        assertThatIllegalArgumentException().isThrownBy {
            signingCertStore.query { getPrivateKey(alias, "entrypassword") }
        }.withMessage("Unrecognised algorithm: 1.3.6.1.4.1.50530.1.2")
    }
}
