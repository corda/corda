package net.corda.node.utilities.registration

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.X509Utilities
import net.corda.core.exists
import net.corda.core.utilities.ALICE
import net.corda.testing.TestNodeConfiguration
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkRegistrationHelperTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun buildKeyStore() {
        val id = SecureHash.randomSHA256().toString()

        val certs = arrayOf(X509Utilities.createSelfSignedCACert("CORDA_CLIENT_CA").certificate,
                X509Utilities.createSelfSignedCACert("CORDA_INTERMEDIATE_CA").certificate,
                X509Utilities.createSelfSignedCACert("CORDA_ROOT_CA").certificate)

        val certService: NetworkRegistrationService = mock {
            on { submitRequest(any()) }.then { id }
            on { retrieveCertificates(eq(id)) }.then { certs }
        }

        val config = TestNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name,
                networkMapService = null)

        assertFalse(config.keyStoreFile.exists())
        assertFalse(config.trustStoreFile.exists())

        NetworkRegistrationHelper(config, certService).buildKeystore()

        assertTrue(config.keyStoreFile.exists())
        assertTrue(config.trustStoreFile.exists())

        X509Utilities.loadKeyStore(config.keyStoreFile, config.keyStorePassword).run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA_PRIVATE_KEY))
            val certificateChain = getCertificateChain(X509Utilities.CORDA_CLIENT_CA)
            assertEquals(3, certificateChain.size)
            assertTrue(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA_PRIVATE_KEY))
        }

        X509Utilities.loadKeyStore(config.trustStoreFile, config.trustStorePassword).run {
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA_PRIVATE_KEY))
            assertFalse(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY))
            assertTrue(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertFalse(containsAlias(X509Utilities.CORDA_ROOT_CA_PRIVATE_KEY))
        }
    }
}
