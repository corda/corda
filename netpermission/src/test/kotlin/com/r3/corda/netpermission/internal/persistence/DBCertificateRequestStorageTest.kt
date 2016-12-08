package com.r3.corda.netpermission.internal.persistence

import net.corda.core.crypto.X509Utilities
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.makeTestDataSourceProperties
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DBCertificateRequestStorageTest {
    val intermediateCA = X509Utilities.createSelfSignedCACert("Corda Node Intermediate CA")

    @Test
    fun `test save request`() {
        val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
        val request = CertificationData("", "", X509Utilities.createCertificateSigningRequest("LegalName", "London", "admin@test.com", keyPair))

        val (connection, db) = configureDatabase(makeTestDataSourceProperties())
        connection.use {
            val storage = DBCertificateRequestStorage(db)
            val requestId = storage.saveRequest(request)

            assertNotNull(storage.getRequest(requestId)).apply {
                assertEquals(request.hostName, hostName)
                assertEquals(request.ipAddr, ipAddr)
                assertEquals(request.request, this.request)
            }
        }
    }

    @Test
    fun `test pending request`() {
        val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
        val request = CertificationData("", "", X509Utilities.createCertificateSigningRequest("LegalName", "London", "admin@test.com", keyPair))

        val (connection, db) = configureDatabase(makeTestDataSourceProperties())
        connection.use {
            val storage = DBCertificateRequestStorage(db)
            val requestId = storage.saveRequest(request)
            storage.pendingRequestIds().apply {
                assertTrue(isNotEmpty())
                assertEquals(1, size)
                assertEquals(requestId, first())
            }
        }
    }

    @Test
    fun `test save certificate`() {
        val keyPair = X509Utilities.generateECDSAKeyPairForSSL()
        val request = CertificationData("", "", X509Utilities.createCertificateSigningRequest("LegalName", "London", "admin@test.com", keyPair))

        val (connection, db) = configureDatabase(makeTestDataSourceProperties())
        connection.use {
            val storage = DBCertificateRequestStorage(db)
            // Add request to DB.
            val requestId = storage.saveRequest(request)
            // Pending request should equals to 1.
            assertEquals(1, storage.pendingRequestIds().size)
            // Certificate should be empty.
            assertNull(storage.getCertificate(requestId))
            // Store certificate to DB.
            storage.saveCertificate(requestId, {
                JcaPKCS10CertificationRequest(it.request).run {
                    X509Utilities.createServerCert(subject, publicKey, intermediateCA,
                            if (it.ipAddr == it.hostName) listOf() else listOf(it.hostName), listOf(it.ipAddr))
                }
            })
            // Check certificate is stored in DB correctly.
            assertNotNull(storage.getCertificate(requestId)).apply {
                assertEquals(keyPair.public, this.publicKey)
            }
            // Pending request should be empty.
            assertTrue(storage.pendingRequestIds().isEmpty())
        }
    }
}