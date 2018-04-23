/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createDevNodeCaCertPath
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.Rule
import java.security.KeyPair
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

abstract class TestBase {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    protected fun certificateSigningRequest(
            requestId: String = SecureHash.randomSHA256().toString(),
            status: RequestStatus = RequestStatus.NEW,
            legalName: CordaX500Name = ALICE_NAME,
            publicKeyHash: SecureHash = SecureHash.randomSHA256(),
            remark: String = "Test remark",
            request: PKCS10CertificationRequest = mock(),
            certData: CertificateData = mock(),
            modifiedBy: String = "Test"
    ): CertificateSigningRequest {
        return CertificateSigningRequest(
                requestId = requestId,
                status = status,
                legalName = legalName,
                publicKeyHash = publicKeyHash,
                remark = remark,
                certData = certData,
                request = request,
                modifiedBy = modifiedBy
        )
    }

    protected fun certificateData(certStatus: CertificateStatus = CertificateStatus.VALID,
                                  certPath: CertPath = mock()): CertificateData {
        return CertificateData(
                certStatus = certStatus,
                certPath = certPath
        )
    }

    private fun generateSignedCertPath(csr: PKCS10CertificationRequest, keyPair: KeyPair): CertPath {
        return JcaPKCS10CertificationRequest(csr).run {
            val (rootCa, intermediateCa, nodeCa) = createDevNodeCaCertPath(CordaX500Name.build(X500Principal(subject.encoded)), keyPair)
            X509Utilities.buildCertPath(nodeCa.certificate, intermediateCa.certificate, rootCa.certificate)
        }
    }

    protected fun createNodeCertificate(csrStorage: CertificateSigningRequestStorage, legalName: String = "LegalName"): X509Certificate {
        val (csr, nodeKeyPair) = createRequest(legalName, certRole = CertRole.NODE_CA)
        // Add request to DB.
        val requestId = csrStorage.saveRequest(csr)
        csrStorage.markRequestTicketCreated(requestId)
        csrStorage.approveRequest(requestId, "Approver")
        val certPath = generateSignedCertPath(csr, nodeKeyPair)
        csrStorage.putCertificatePath(
                requestId,
                certPath,
                CertificateSigningRequestStorage.DOORMAN_SIGNATURE
        )
        return certPath.x509Certificates.first()
    }

}