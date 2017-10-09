package net.corda.signing.persistence

import com.r3.corda.doorman.buildCertPath
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.configureDatabase
import net.corda.signing.persistence.DBCertificateRequestStorage.CertificateSigningRequest
import net.corda.signing.persistence.DBCertificateRequestStorage.Status
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DBCertificateRequestStorageTest {
    private val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Node Intermediate CA", organisation = "R3 Ltd", locality = "London", country = "GB").x500Name, intermediateCAKey)
    private lateinit var storage: DBCertificateRequestStorage
    private lateinit var persistence: CordaPersistence

    @Before
    fun startDb() {
        persistence = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties(), { SigningServerSchemaService() }, createIdentityService = { throw UnsupportedOperationException() })
        storage = DBCertificateRequestStorage(persistence)
    }

    @After
    fun closeDb() {
        persistence.close()
    }

    @Test
    fun `getApprovedRequests returns only requests with status APPROVED`() {
        // given
        (1..10).forEach {
            createAndPersistRequest("Bank$it", Status.Approved)
        }
        (11..15).forEach {
            createAndPersistRequest("Bank$it", Status.Signed)
        }
        // when
        val result = storage.getApprovedRequests()

        // then
        assertEquals(10, result.size)
        result.forEach {
            val request = getRequestById(it.requestId)
            assertNotNull(request)
            assertEquals(Status.Approved, request?.status)
        }
    }

    @Test
    fun `sign changes the status of requests to SIGNED`() {
        // given
        (1..10).map {
            createAndPersistRequest("Bank$it")
        }
        val requests = storage.getApprovedRequests()

        // Create a signed certificate
        requests.forEach { certifyAndSign(it) }

        val signers = listOf("TestUserA", "TestUserB")

        // when
        storage.sign(requests, signers)

        // then
        requests.forEach {
            val request = getRequestById(it.requestId)
            assertNotNull(request)
            assertEquals(Status.Signed, request?.status)
            assertEquals(signers.toString(), request?.signedBy.toString())
            assertNotNull(request?.certificatePath)
        }
    }

    private fun certifyAndSign(approvedRequestData: ApprovedCertificateRequestData) {
        JcaPKCS10CertificationRequest(approvedRequestData.request).run {
            val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, subject))), arrayOf())
            approvedRequestData.certPath = buildCertPath(
                    X509Utilities.createCertificate(
                            CertificateType.CLIENT_CA,
                            intermediateCACert,
                            intermediateCAKey,
                            subject,
                            publicKey,
                            nameConstraints = nameConstraints).toX509Certificate())
        }

    }

    private fun getRequestById(requestId: String): CertificateSigningRequest? {
        return persistence.transaction {
            singleRequestWhere { builder, path ->
                builder.equal(path.get<String>(CertificateSigningRequest::requestId.name), requestId)
            }
        }
    }

    private fun singleRequestWhere(predicate: (CriteriaBuilder, Path<CertificateSigningRequest>) -> Predicate): CertificateSigningRequest? {
        return persistence.transaction {
            val builder = session.criteriaBuilder
            val criteriaQuery = builder.createQuery(CertificateSigningRequest::class.java)
            val query = criteriaQuery.from(CertificateSigningRequest::class.java).run {
                criteriaQuery.where(predicate(builder, this))
            }
            session.createQuery(query).uniqueResultOptional().orElse(null)
        }
    }

    private fun createAndPersistRequest(legalName: String, status: Status = Status.Approved): String {
        val requestId = SecureHash.randomSHA256().toString()
        persistence.transaction {
            val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val x500Name = CordaX500Name(organisation = legalName, locality = "London", country = "GB").x500Name
            session.save(CertificateSigningRequest(
                    requestId = requestId,
                    status = status,
                    request = X509Utilities.createCertificateSigningRequest(x500Name, "my@mail.com", keyPair).encoded
            ))
        }
        return requestId
    }

    private fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }

    private fun makeTestDatabaseProperties(key: String? = null, value: String? = null): Properties {
        val props = Properties()
        props.setProperty("transactionIsolationLevel", "repeatableRead") //for other possible values see net.corda.node.utilities.CordaPeristence.parserTransactionIsolationLevel(String)
        if (key != null) {
            props.setProperty(key, value)
        }
        return props
    }
}

private object CertificateUtilities {
    fun toX509Certificate(byteArray: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509").generateCertificate(ByteArrayInputStream(byteArray)) as X509Certificate
    }
}

/**
 * Converts [X509CertificateHolder] to standard Java [Certificate]
 */
private fun X509CertificateHolder.toX509Certificate(): Certificate = CertificateUtilities.toX509Certificate(encoded)