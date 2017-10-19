package net.corda.core.node.services

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.net.HttpURLConnection
import java.net.URL
import javax.ws.rs.core.MediaType

interface NetworkMapClient {
    /**
     *  Publish node info to network map service.
     */
    fun publish(signedNodeInfo: SignedData<NodeInfo>)

    /**
     *  Retrieve [NetworkMap] from the network map service containing list of node info hashes and network parameter hash.
     */
    // TODO: Use NetworkMap object when available.
    fun getNetworkMap(): List<SecureHash>

    /**
     *  Retrieve [NodeInfo] from network map service using the node info hash.
     */
    fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo?

    // TODO: Implement getNetworkParameter when its available.
    //fun getNetworkParameter(networkParameterHash: SecureHash): NetworkParameter
}

class HTTPNetworkMapClient(private val networkMapUrl: String) : NetworkMapClient {
    override fun publish(signedNodeInfo: SignedData<NodeInfo>) {
        val publishURL = URL("$networkMapUrl/publish")
        val conn = publishURL.openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
        conn.outputStream.write(signedNodeInfo.serialize().bytes)
        when (conn.responseCode) {
            HttpURLConnection.HTTP_OK -> return
            HttpURLConnection.HTTP_UNAUTHORIZED -> throw IllegalArgumentException(conn.errorStream.bufferedReader().readLine())
            else -> throw IllegalArgumentException("Unexpected response code ${conn.responseCode}, response error message: '${conn.errorStream.bufferedReader().readLines()}'")
        }
    }

    override fun getNetworkMap(): List<SecureHash> {
        val conn = URL(networkMapUrl).openConnection() as HttpURLConnection

        return when (conn.responseCode) {
            HttpURLConnection.HTTP_OK -> {
                val response = conn.inputStream.bufferedReader().use { it.readLine() }
                ObjectMapper().readValue(response, List::class.java).map { SecureHash.parse(it.toString()) }
            }
            else -> throw IllegalArgumentException("Unexpected response code ${conn.responseCode}, response error message: '${conn.errorStream.bufferedReader().readLines()}'")
        }
    }

    override fun getNodeInfo(nodeInfoHash: SecureHash): NodeInfo? {
        val nodeInfoURL = URL("$networkMapUrl/$nodeInfoHash")
        val conn = nodeInfoURL.openConnection() as HttpURLConnection

        return when (conn.responseCode) {
            HttpURLConnection.HTTP_OK -> conn.inputStream.readBytes().deserialize()
            HttpURLConnection.HTTP_NOT_FOUND -> null
            else -> throw IllegalArgumentException("Unexpected response code ${conn.responseCode}, response error message: '${conn.errorStream.bufferedReader().readLines()}'")
        }
    }
}