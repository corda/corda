/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.common.utils.getCertRole
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.certificateType
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.security.cert.CertPath
import javax.security.auth.x500.X500Principal

interface CsrHandler {
    fun saveRequest(rawRequest: PKCS10CertificationRequest): String
    fun processRequests()
    fun getResponse(requestId: String): CertificateResponse
}

class DefaultCsrHandler(private val storage: CertificateSigningRequestStorage,
                        private val csrCertPathAndKey: CertPathAndKey?) : CsrHandler {

    override fun processRequests() {
        if (csrCertPathAndKey == null) return
        storage.getRequests(RequestStatus.APPROVED).forEach {
            val nodeCertPath = createSignedNodeCertificate(it.request, csrCertPathAndKey)
            // Since Doorman is deployed in the auto-signing mode (i.e. signer != null),
            // we use DOORMAN_SIGNATURE as the signer.
            storage.putCertificatePath(it.requestId, nodeCertPath, DOORMAN_SIGNATURE)
        }
    }

    override fun saveRequest(rawRequest: PKCS10CertificationRequest): String = storage.saveRequest(rawRequest)

    override fun getResponse(requestId: String): CertificateResponse {
        val response = storage.getRequest(requestId)
        return when (response?.status) {
            RequestStatus.NEW, RequestStatus.APPROVED, RequestStatus.TICKET_CREATED, null -> CertificateResponse.NotReady
            RequestStatus.REJECTED -> CertificateResponse.Unauthorised(response.remark ?: "Unknown reason")
            RequestStatus.DONE -> CertificateResponse.Ready(requireNotNull(response.certData?.certPath) { "Certificate should not be null." })
        }
    }

    private fun createSignedNodeCertificate(certificationRequest: PKCS10CertificationRequest,
                                            csrCertPathAndKey: CertPathAndKey): CertPath {
        // The sub certs issued by the client must satisfy this directory name (or legal name in Corda) constraints,
        // sub certs' directory name must be within client CA's name's subtree,
        // please see [sun.security.x509.X500Name.isWithinSubtree()] for more information.
        // We assume all attributes in the subject name has been checked prior approval.
        // TODO: add validation to subject name.
        val request = JcaPKCS10CertificationRequest(certificationRequest)
        val certRole = request.getCertRole()
        val nameConstraints = NameConstraints(
                arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, request.subject))),
                arrayOf())
        val nodeCaCert = X509Utilities.createCertificate(
                certRole.certificateType,
                csrCertPathAndKey.certPath[0],
                csrCertPathAndKey.toKeyPair(),
                X500Principal(request.subject.encoded),
                request.publicKey,
                nameConstraints = nameConstraints)
        return X509CertificateFactory().generateCertPath(listOf(nodeCaCert) + csrCertPathAndKey.certPath)
    }
}
