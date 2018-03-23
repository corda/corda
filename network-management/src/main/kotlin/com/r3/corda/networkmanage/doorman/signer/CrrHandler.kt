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

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestStorage
import net.corda.nodeapi.internal.network.CertificateRevocationRequest

interface CrrHandler {
    fun processRequests()
    fun saveRevocationRequest(request: CertificateRevocationRequest): String
}

class DefaultCrrHandler(private val crrStorage: CertificateRevocationRequestStorage,
                        private val localCrlHandler: LocalCrlHandler?) : CrrHandler {
    override fun saveRevocationRequest(request: CertificateRevocationRequest): String = crrStorage.saveRevocationRequest(request)
    override fun processRequests() {
        localCrlHandler?.signCrl()
    }
}
