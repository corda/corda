package net.corda.sgx.attestation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.sgx.attestation.entities.*
import net.corda.sgx.attestation.service.messages.*
import net.corda.sgx.enclave.ECKey
import net.corda.sgx.system.ExtendedGroupIdentifier
import net.corda.sgx.system.GroupIdentifier
import net.corda.sgx.system.SgxSystem
import net.corda.sgx.system.value
import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Client used to talk to the Remote Attestation ISV.
 *
 * @property endpoint The base URL of the ISV service.
 */
open class ISVHttpClient(
        private val endpoint: String = "http://localhost:${portNumber()}",
        private val name: String = "Client"
) : ISVClient {

    private companion object {

        @JvmStatic
        private val log: Logger = LoggerFactory
                .getLogger(ISVHttpClient::class.java)

        fun portNumber(): Int {
            val portNumber = System.getenv("PORT") ?: "9080"
            return Integer.parseUnsignedInt(portNumber)
        }

    }

    private var httpClient: CloseableHttpClient
    private val mapper = ObjectMapper().registerModule(JavaTimeModule())

    init {
        val httpRequestConfig = RequestConfig.custom()
                .setConnectTimeout(5_000)
                .setSocketTimeout(5_000)
                .build()
        val httpSocketConfig = SocketConfig.custom()
                .setSoReuseAddress(true)
                .setTcpNoDelay(true)
                .build()
        val connectionManager = BasicHttpClientConnectionManager()
                .apply { socketConfig = httpSocketConfig }
        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(httpRequestConfig)
                .build()
        log.debug("$name : Talking to ISV on $endpoint")
    }

    override fun requestChallenge(): Challenge {
        val request = HttpGet("$endpoint/attest/provision")
        httpClient.execute(request).use { response ->
            val body = EntityUtils.toString(response.entity)
            log.debug("$name : Challenge <- ${response.statusLine}")
            log.debug("$name : Challenge <- $body")
            if (response.statusLine.statusCode < 200 ||
                    response.statusLine.statusCode >= 300)
            {
                throw AttestationException(response.statusLine.toString())
            }
            val challenge = read<ChallengeResponse>(body)

            if (SC_OK != response.statusLine.statusCode
                    || challenge.nonce.isEmpty()) {
                throw AttestationException("Failed to request challenge")
            }

            return Challenge(
                    challenge.nonce,
                    ECKey.fromBytes(challenge.serviceKey)
            )
        }
    }

    override fun validateExtendedGroupIdentifier(
            extendedGroupId: ExtendedGroupIdentifier
    ): Boolean {
        val message0 = Message0(extendedGroupId.value)
        val request = HttpPost("$endpoint/attest/msg0")
        log.debug("$name : MSG0 -> ${mapper.writeValueAsString(message0)}")
        request.entity = StringEntity(
                mapper.writeValueAsString(message0),
                APPLICATION_JSON
        )
        httpClient.execute(request).use { response ->
            log.debug("$name : MSG0 <- ${response.statusLine}")
            return SC_OK == response.statusLine.statusCode
        }
    }

    override fun sendPublicKeyAndGroupIdentifier(
            publicKey: ECKey,
            groupIdentifier: GroupIdentifier
    ): ChallengerDetails {
        val message1 = Message1(
                ga = publicKey.bytes,
                platformGID = groupIdentifier.value()
        )
        val request = HttpPost("$endpoint/attest/msg1")
        log.debug("$name : MSG1 -> ${mapper.writeValueAsString(message1)}")
        request.entity = StringEntity(
                mapper.writeValueAsString(message1),
                APPLICATION_JSON
        )
        httpClient.execute(request).use { response ->
            val body = EntityUtils.toString(response.entity)
            log.debug("$name : MSG2 <- ${response.statusLine}")
            log.debug("$name : MSG2 <- $body")
            if (response.statusLine.statusCode < 200 ||
                    response.statusLine.statusCode >= 300)
            {
                throw AttestationException(response.statusLine.toString())
            }
            val message2 = read<Message2>(body)

            if (SC_OK != response.statusLine.statusCode) {
                throw AttestationException("Invalid response from server")
            }

            message2.spid
            val spid = hexStringToByteArray(message2.spid)
            return ChallengerDetails(
                    publicKey = ECKey.fromBytes(message2.gb),
                    serviceProviderIdentifier = spid,
                    quoteType = if (message2.linkableQuote) {
                        QuoteType.LINKABLE
                    } else {
                        QuoteType.UNLINKABLE
                    },
                    keyDerivationFunctionIdentifier = message2.keyDerivationFuncId.toShort(),
                    signature = message2.signatureGbGa,
                    messageAuthenticationCode = message2.aesCMAC,
                    signatureRevocationList = message2.revocationList
            )
        }
    }

    override fun submitQuote(
            challenge: Challenge,
            quote: Quote
    ): AttestationResult {
        val message3 = Message3(
                aesCMAC = quote.messageAuthenticationCode,
                ga = quote.publicKey.bytes,
                quote = quote.payload,
                securityManifest = quote.securityProperties,
                nonce = challenge.nonce
        )
        val request = HttpPost("$endpoint/attest/msg3")
        log.debug("$name : MSG3 -> ${mapper.writeValueAsString(message3)}")
        request.entity = StringEntity(
                mapper.writeValueAsString(message3),
                APPLICATION_JSON
        )
        httpClient.execute(request).use { response ->
            val body = EntityUtils.toString(response.entity)
            log.debug("$name : MSG4 <- ${response.statusLine}")
            for (header in response.allHeaders) {
                log.trace("$name : MSG4 <- ${header.name} = ${header.value}")
            }
            log.debug("$name : MSG4 <- $body")
            if (response.statusLine.statusCode < 200 ||
                    response.statusLine.statusCode >= 300)
            {
                throw AttestationException(response.statusLine.reasonPhrase)
            }
            val message4 = read<Message4>(body)
            return AttestationResult(
                    quoteStatus = SgxSystem.quoteStatusFromString(message4.quoteStatus),
                    attestationResultMessage = message4.platformInfo ?: byteArrayOf(),
                    aesCMAC = message4.aesCMAC ?: byteArrayOf(),
                    secret = message4.secret,
                    secretHash = message4.secretHash,
                    secretIV = message4.secretIV,
                    status = if (SC_OK == response.statusLine.statusCode) {
                        AttestationStatus.SUCCESS
                    } else {
                        AttestationStatus.FAIL
                    }
            )
        }
    }

    private inline fun <reified T> read(value: String): T =
            mapper.readValue(value, T::class.java) as T

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val c1 = Character.digit(s[i], 16) shl 4
            val c2 = Character.digit(s[i + 1], 16)
            data[i / 2] = (c1 + c2).toByte()
            i += 2
        }
        return data
    }

}
