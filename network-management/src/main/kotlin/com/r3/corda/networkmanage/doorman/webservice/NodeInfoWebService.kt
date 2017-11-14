package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.hashString
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService.Companion.networkMapPath
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NetworkParameters
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

@Path(networkMapPath)
class NodeInfoWebService(private val nodeInfoStorage: NodeInfoStorage,
                         private val networkMapStorage: NetworkMapStorage,
                         private val signer: LocalSigner? = null) {
    companion object {
        const val networkMapPath = "network-map"
    }

    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    fun registerNode(input: InputStream): Response {
        // TODO: Use JSON instead.
        val registrationData = input.readBytes().deserialize<SignedData<NodeInfo>>()

        val nodeInfo = registrationData.verified()
        val digitalSignature = registrationData.sig

        val certPath = nodeInfoStorage.getCertificatePath(SecureHash.parse(digitalSignature.by.hashString()))
        return if (certPath != null) {
            try {
                val serializedNodeInfo = nodeInfo.serialize().bytes
                val nodeCAPubKey = certPath.certificates.first().publicKey
                // Validate node public key
                nodeInfo.legalIdentitiesAndCerts.forEach {
                    require(it.certPath.certificates.any { it.publicKey == nodeCAPubKey })
                }
                require(Crypto.doVerify(nodeCAPubKey, digitalSignature.bytes, serializedNodeInfo))
                // Store the NodeInfo and notify registration listener
                nodeInfoStorage.putNodeInfo(nodeInfo, signer?.sign(serializedNodeInfo)?.signature)
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
        return nodeInfoStorage.getSignedNodeInfo(SecureHash.parse(nodeInfoHash))?.let {
            ok(it.serialize().bytes).build()
        } ?: status(Response.Status.NOT_FOUND).build()
    }

    @GET
    @Path("my-ip")
    fun myIp(@Context request: HttpServletRequest): Response {
        // TODO: Verify this returns IP correctly.
        return ok(request.getHeader("X-Forwarded-For")?.split(",")?.first() ?: "${request.remoteHost}:${request.remotePort}").build()
    }
}
