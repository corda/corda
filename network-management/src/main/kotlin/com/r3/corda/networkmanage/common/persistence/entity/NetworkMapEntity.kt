package com.r3.corda.networkmanage.common.persistence.entity

import com.r3.corda.networkmanage.common.signer.SignatureAndCertPath
import net.corda.core.crypto.DigitalSignature
import net.corda.core.serialization.deserialize
import javax.persistence.*

@Entity
@Table(name = "network_map")
class NetworkMapEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val version: Long? = null,

        // Reverting relation ownership due to (potentially) unlimited number of node info items.
        @OneToMany(mappedBy = "networkMap", fetch = FetchType.LAZY)
        val nodeInfoList: List<NodeInfoEntity> = mutableListOf(),

        @OneToOne
        @JoinColumn(name = "network_parameters")
        val parameters: NetworkParametersEntity,

        @Lob
        @Column(name = "signature_bytes")
        val signatureBytes: ByteArray,

        @Lob
        @Column(name = "certificate_path_bytes")
        val certificatePathBytes: ByteArray
) {
    /**
     * Deserializes NetworkMapEntity.signatureBytes into the [SignatureAndCertPath] instance
     */
    fun signatureAndCertificate(): SignatureAndCertPath? {
        return SignatureAndCertPath(DigitalSignature(signatureBytes), certificatePathBytes.deserialize())
    }

}