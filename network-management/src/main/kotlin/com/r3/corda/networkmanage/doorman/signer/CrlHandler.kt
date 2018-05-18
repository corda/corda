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

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestStorage
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.signer.CertificateRevocationListSigner
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.net.URL
import java.time.Duration

class LocalCrlHandler(private val crrStorage: CertificateRevocationRequestStorage,
                      private val crlStorage: CertificateRevocationListStorage,
                      issuerCertAndKey: CertificateAndKeyPair,
                      crlUpdateInterval: Duration,
                      crlEndpoint: URL) {
    private companion object {
        private val logger = contextLogger()
    }

    private val crlSigner = CertificateRevocationListSigner(
            crlStorage,
            issuerCertAndKey.certificate,
            crlUpdateInterval,
            crlEndpoint,
            LocalSigner(issuerCertAndKey))

    fun signCrl() {
        if (crlStorage.getCertificateRevocationList(CrlIssuer.DOORMAN) == null) {
            val crl = crlSigner.createSignedCRL(emptyList(), emptyList(), DOORMAN_SIGNATURE)
            logger.info("Saving a new empty CRL: $crl")
            return
        }
        logger.info("Executing CRL signing...")
        val approvedRequests = crrStorage.getRevocationRequests(RequestStatus.APPROVED)
        logger.debug("Approved certificate revocation requests retrieved: $approvedRequests")
        if (approvedRequests.isEmpty()) {
            // Nothing to add to the current CRL
            logger.debug("There are no APPROVED certificate revocation requests. Aborting CRL signing.")
            return
        }
        val currentRequests = crrStorage.getRevocationRequests(RequestStatus.DONE)
        logger.debug("Existing certificate revocation requests retrieved: $currentRequests")
        val crl = crlSigner.createSignedCRL(approvedRequests, currentRequests, DOORMAN_SIGNATURE)
        logger.info("New CRL signed: $crl")
    }
}
