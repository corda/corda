package net.corda.attestation.message

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("message")
class AttestationError(@param:JsonProperty("message") val message: String)