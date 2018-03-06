/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData

/**
 * Encapsulates the logic related to the certificate signing process.
 */
interface CertificateSigningRequestSigner {

    /**
     * Signs the provided list of [ApprovedCertificateRequestData] with the key/certificate chosen
     * by the implementing class.
     */
    fun sign(toSign: List<ApprovedCertificateRequestData>)

}