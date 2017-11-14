package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.deserialize
import javax.persistence.*

@Entity
@Table(name = "network_parameters")
class NetworkParametersEntity(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        val version: Long? = null,

        @Column(name = "hash", length = 64, unique = true)
        val parametersHash: String,

        @Lob
        @Column(name = "bytes")
        val parametersBytes: ByteArray
) {
    fun networkParameters(): NetworkParameters = parametersBytes.deserialize()
}