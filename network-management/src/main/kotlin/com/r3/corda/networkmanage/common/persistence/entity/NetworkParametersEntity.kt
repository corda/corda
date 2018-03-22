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
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.security.cert.X509Certificate
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "network_parameters", indexes = arrayOf(Index(name = "IDX_NP_HASH", columnList = "hash")))
class NetworkParametersEntity(
        @Id
        @Column(name = "hash", length = 64, nullable = false)
        val hash: String,

        @Column(nullable = false)
        val created: Instant = Instant.now(),

        @Lob
        @Column(name = "parameters_bytes", nullable = false)
        @Convert(converter = NetworkParametersConverter::class)
        val networkParameters: NetworkParameters,

        // Both of the fields below are nullable, because of the way we sign network map data. NetworkParameters can be
        // inserted into database without signature. Then signing service will sign them.
        @Lob
        @Column(name = "signature")
        val signature: ByteArray?,

        @Lob
        @Column(name = "cert")
        @Convert(converter = X509CertificateConverter::class)
        val certificate: X509Certificate?
) {
    val isSigned: Boolean get() = certificate != null && signature != null

    fun toSignedNetworkParameters(): SignedNetworkParameters {
        if (certificate == null || signature == null) throw IllegalStateException("Network parameters entity is not signed: $hash")
        return SignedNetworkParameters(networkParameters.serialize(), DigitalSignatureWithCert(certificate, signature))
    }

    fun copy(parametersHash: String = this.hash,
             created: Instant = this.created,
             networkParameters: NetworkParameters = this.networkParameters,
             signature: ByteArray? = this.signature,
             certificate: X509Certificate? = this.certificate
    ): NetworkParametersEntity {
        return NetworkParametersEntity(
                hash = parametersHash,
                created = created,
                networkParameters = networkParameters,
                signature = signature,
                certificate = certificate
        )
    }
}
