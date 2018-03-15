/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import javax.persistence.*

@Entity
@Table(name = "network_map")
class NetworkMapEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val version: Long? = null,

        @Lob
        @Column(name = "serialized_network_map")
        val networkMap: ByteArray,

        @Lob
        @Column(name = "signature")
        val signature: ByteArray,

        @Lob
        @Column(name = "certificate")
        val certificate: ByteArray
) {
    fun toNetworkMap(): NetworkMap = networkMap.deserialize()

    fun toSignedNetworkMap(): SignedNetworkMap {
        return SignedNetworkMap(
                SerializedBytes(networkMap),
                DigitalSignatureWithCert(X509CertificateFactory().generateCertificate(certificate.inputStream()), signature)
        )
    }
}
