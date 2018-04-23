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

/**
 * Provides an API for storing signed CSRs (Certificate Signing Requests).
 */
interface SignedCertificateSigningRequestStorage {

    /**
     * Returns all certificate signing requests that have been approved for signing.
     */
    fun getApprovedRequests(): List<ApprovedCertificateRequestData>

    /**
     * Marks the database CSR entries as signed. Also it persists the certificate and the signature in the database.
     *
     * @param requests Signed requests that are to be stored.
     * @param signers List of user names that signed those requests. To be specific, each request has been signed by all of those users.
     */
    fun store(requests: List<ApprovedCertificateRequestData>, signer: String)
}