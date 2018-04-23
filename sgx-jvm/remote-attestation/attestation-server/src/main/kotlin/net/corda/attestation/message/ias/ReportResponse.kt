package net.corda.attestation.message.ias

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDateTime

@JsonPropertyOrder(
    "nonce",
    "id",
    "timestamp",
    "epidPseudonym",
    "isvEnclaveQuoteStatus",
    "isvEnclaveQuoteBody",
    "pseManifestStatus",
    "pseManifestHash",
    "platformInfoBlob",
    "revocationReason"
)
@JsonInclude(NON_NULL)
class ReportResponse(
    @param:JsonProperty("id")
    val id: String,

    @param:JsonProperty("isvEnclaveQuoteStatus")
    val isvEnclaveQuoteStatus: QuoteStatus,

    @param:JsonProperty("isvEnclaveQuoteBody")
    val isvEnclaveQuoteBody: ByteArray,

    @param:JsonProperty("platformInfoBlob")
    @get:JsonSerialize(using = HexadecimalSerialiser::class)
    @get:JsonDeserialize(using = HexadecimalDeserialiser::class)
    val platformInfoBlob: ByteArray? = null,

    @param:JsonProperty("revocationReason")
    val revocationReason: Int? = null,

    @param:JsonProperty("pseManifestStatus")
    val pseManifestStatus: ManifestStatus? = null,

    @param:JsonProperty("pseManifestHash")
    val pseManifestHash: String? = null,

    @param:JsonProperty("nonce")
    val nonce: String? = null,

    @param:JsonProperty("epidPseudonym")
    val epidPseudonym: ByteArray? = null,

    @param:JsonProperty("timestamp")
    @field:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    val timestamp: LocalDateTime
)
