package com.r3.corda.networkmanage

import com.r3.corda.networkmanage.common.persistence.entity.NetworkMapEntity
import com.r3.corda.networkmanage.common.persistence.entity.NetworkParametersEntity
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.testing.common.internal.testNetworkParameters

fun createNetworkParametersEntity(signingCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa(),
                                  networkParameters: NetworkParameters = testNetworkParameters()): NetworkParametersEntity {
    val signedNetParams = signingCertAndKeyPair.sign(networkParameters)
    return NetworkParametersEntity(
            hash = signedNetParams.raw.hash.toString(),
            networkParameters = networkParameters,
            signature = signedNetParams.sig.bytes,
            certificate = signedNetParams.sig.by
    )
}

fun createNetworkParametersEntityUnsigned(networkParameters: NetworkParameters = testNetworkParameters()): NetworkParametersEntity {
    return NetworkParametersEntity(
            hash = networkParameters.serialize().hash.toString(),
            networkParameters = networkParameters,
            signature = null,
            certificate = null
    )
}

fun createNetworkMapEntity(signingCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa(),
                           netParamsEntity: NetworkParametersEntity,
                           nodeInfoHashes: List<SecureHash> = emptyList(),
                           parametersUpdate: ParametersUpdate? = null): NetworkMapEntity {
    val networkMap = NetworkMap(nodeInfoHashes, SecureHash.parse(netParamsEntity.hash), parametersUpdate)
    val signedNetworkMap = signingCertAndKeyPair.sign(networkMap)
    return NetworkMapEntity(
            networkMap = networkMap,
            signature = signedNetworkMap.sig.bytes,
            certificate = signedNetworkMap.sig.by,
            networkParameters = netParamsEntity
    )
}

fun createNetworkMapEntity(signingCertAndKeyPair: CertificateAndKeyPair = createDevNetworkMapCa(),
                           networkParameters: NetworkParameters = testNetworkParameters(),
                           nodeInfoHashes: List<SecureHash> = emptyList()): NetworkMapEntity {
    val netParamsEntity = createNetworkParametersEntity(signingCertAndKeyPair, networkParameters)
    return createNetworkMapEntity(signingCertAndKeyPair, netParamsEntity, nodeInfoHashes)
}
