package com.r3.corda.netpermission.internal.persistence

import net.corda.core.crypto.SecureHash
import java.security.cert.Certificate
import java.util.*

class InMemoryCertificationRequestStorage : CertificationRequestStorage {
    private val requestStore = HashMap<String, CertificationData>()
    private val certificateStore = HashMap<String, Certificate>()

    override fun pendingRequestIds(): List<String> {
        return requestStore.keys.filter { !certificateStore.keys.contains(it) }
    }

    override fun getCertificate(requestId: String): Certificate? {
        return certificateStore[requestId]
    }

    override fun saveCertificate(requestId: String, certificateGenerator: (CertificationData) -> Certificate) {
        requestStore[requestId]?.let {
            certificateStore.putIfAbsent(requestId, certificateGenerator(it))
        }
    }

    override fun getRequest(requestId: String): CertificationData? {
        return requestStore[requestId]
    }

    override fun saveRequest(certificationData: CertificationData): String {
        val requestId = SecureHash.randomSHA256().toString()
        requestStore.put(requestId, certificationData)
        return requestId
    }
}