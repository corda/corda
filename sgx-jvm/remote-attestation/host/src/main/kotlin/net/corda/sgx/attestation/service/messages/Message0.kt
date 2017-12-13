package net.corda.sgx.attestation.service.messages

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@Suppress("KDocMissingDocumentation")
@JsonPropertyOrder("extendedGID")
@JsonInclude(NON_NULL)
class Message0(

        @param:JsonProperty("extendedGID")
        val extendedGID: Int

)