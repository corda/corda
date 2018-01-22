package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.utils.SignedNetworkMap
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.network.NetworkMap
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
