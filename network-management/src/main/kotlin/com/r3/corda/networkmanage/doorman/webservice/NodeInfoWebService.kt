package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.hashString
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService.Companion.NETWORK_MAP_PATH
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.io.InputStream
import java.security.InvalidKeyException
import java.security.SignatureException
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status

@Path(NETWORK_MAP_PATH)
class NodeInfoWebService(private val nodeInfoStorage: NodeInfoStorage,
                         private val networkMapStorage: NetworkMapStorage) {
    companion object {
        const val NETWORK_MAP_PATH = "network-map"
    }

    @POST
    @Path("publish")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun registerNode(input: InputStream): Response {
        val registrationData = input.readBytes().deserialize<SignedData<NodeInfo>>()

        val nodeInfo = registrationData.verified()

        val certPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(nodeInfo.legalIdentitiesAndCerts.first().certPath.certificates.first().publicKey.hashString()))
        return if (certPath != null) {
            try {
                val nodeCAPubKey = certPath.certificates.first().publicKey
                // Validate node public key
                nodeInfo.legalIdentitiesAndCerts.forEach {
                    require(it.certPath.certificates.any { it.publicKey == nodeCAPubKey })
                }
                val digitalSignature = registrationData.sig
                require(Crypto.doVerify(nodeCAPubKey, digitalSignature.bytes, registrationData.raw.bytes))
                // Store the NodeInfo
                nodeInfoStorage.putNodeInfo(registrationData)
                ok()
            } catch (e: Exception) {
                // Catch exceptions thrown by signature verification.
                when (e) {
                    is IllegalArgumentException, is InvalidKeyException, is SignatureException -> status(Response.Status.UNAUTHORIZED).entity(e.message)
                // Rethrow e if its not one of the expected exception, the server will return http 500 internal error.
                    else -> throw e
                }
            }
        } else {
            status(Response.Status.BAD_REQUEST).entity("Unknown node info, this public key is not registered or approved by Corda Doorman.")
        }.build()
    }

    @GET
    fun getNetworkMap(): Response {
        // TODO: Cache the response?
        return ok(networkMapStorage.getCurrentNetworkMap().serialize().bytes).build()
    }

    @GET
    @Path("{nodeInfoHash}")
    fun getNodeInfo(@PathParam("nodeInfoHash") nodeInfoHash: String): Response {
        val nodeInfo = nodeInfoStorage.getNodeInfo(SecureHash.parse(nodeInfoHash))
        return if (nodeInfo != null) {
            ok(nodeInfo.serialize().bytes).build()
        } else {
            status(Response.Status.NOT_FOUND).build()
        }
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        // TODO: Verify this returns IP correctly.
        return ok(request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}").build()
    }
}
