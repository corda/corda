package com.r3corda.netpermission.internal.persistence

import com.r3corda.core.crypto.SecureHash
import java.security.cert.Certificate
import java.util.*

class InMemoryCertificationRequestStorage : CertificationRequestStorage {
    val requestStore = HashMap<String, CertificationData>()
    val certificateStore = HashMap<String, Certificate>()

    override fun getOrElseCreateCertificate(requestId: String, certificateGenerator: () -> Certificate): Certificate {
        return certificateStore.getOrPut(requestId, certificateGenerator)
    }

    override fun getApprovedRequest(requestId: String): CertificationData? {
        return requestStore[requestId]
    }

    override fun saveRequest(certificationData: CertificationData): String {
        val requestId = SecureHash.randomSHA256().toString()
        requestStore.put(requestId, certificationData)
        return requestId
    }
}