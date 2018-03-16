/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.persistence

import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.PersistentCertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.CertPath

data class ApprovedCertificateRequestData(val requestId: String, val request: PKCS10CertificationRequest, var certPath: CertPath? = null)

class DBSignedCertificateRequestStorage(database: CordaPersistence) : SignedCertificateSigningRequestStorage {

    private val storage = PersistentCertificateSigningRequestStorage(database)

    override fun store(requests: List<ApprovedCertificateRequestData>, signer: String) {
        for ((requestId, _, certPath) in requests) {
            storage.putCertificatePath(requestId, certPath!!, signer)
        }
    }

    override fun getApprovedRequests(): List<ApprovedCertificateRequestData> {
        return storage.getRequests(RequestStatus.APPROVED).map { it.toRequestData() }
    }

    private fun CertificateSigningRequest.toRequestData() = ApprovedCertificateRequestData(requestId, request)
}