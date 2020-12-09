package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.Principal
import java.security.PublicKey

@CordaSerializable
data class MemberInfo(
        val memberId: String,
        val party: Party,
        val keys: List<PublicKey>,
        val endpoints: List<EndpointInfo>,
        val status: MemberStatus,
        val softwareVersion: String,
        val platformVersion: Int,
        val role: MemberRole,
        val properties: Map<String, String>
) {
    init {
        require(endpoints.isNotEmpty()) { "Node must have at least one address" }
        require(platformVersion > 0) { "Platform version must be at least 1" }
        require(softwareVersion.isNotEmpty()) { "Node software version must not be blank" }
        require(party.owningKey in keys) { "Identity key must be in the key list" }
    }
}

@CordaSerializable
data class EndpointInfo(
        val connectionURL: String,
        val tlsSubjectName: Principal,
        val protocolVersion: Int
) {
    init {
        require(protocolVersion > 0) { "Endpoint protocol version must be at least 1" }
    }
}

@CordaSerializable
enum class MemberStatus {
    PENDING,
    ACTIVE,
    SUSPENDED
}

@CordaSerializable
enum class MemberRole {
    MANAGER,
    NODE,
    NOTARY
}
