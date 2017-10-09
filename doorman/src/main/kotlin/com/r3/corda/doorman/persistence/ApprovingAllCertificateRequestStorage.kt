package com.r3.corda.doorman.persistence

import net.corda.node.utilities.CordaPersistence

/**
 * This storage automatically approves all created requests.
 */
class ApprovingAllCertificateRequestStorage(database: CordaPersistence) : DBCertificateRequestStorage(database) {
    override fun saveRequest(certificationData: CertificationRequestData): String {
        val requestId = super.saveRequest(certificationData)
        approveRequest(requestId)
        return requestId
    }
}