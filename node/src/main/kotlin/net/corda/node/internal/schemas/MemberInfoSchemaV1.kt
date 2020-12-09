package net.corda.node.internal.schemas

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.EndpointInfo
import net.corda.core.node.MemberInfo
import net.corda.core.node.MemberRole
import net.corda.core.node.MemberStatus
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.security.PublicKey
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.ForeignKey
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.security.auth.x500.X500Principal

object MemberInfoSchema

object MemberInfoSchemaV1 : MappedSchema(
        schemaFamily = MemberInfoSchema.javaClass,
        version = 1,
        mappedTypes = listOf(
                PersistentMemberInfo::class.java,
                PersistentEndpointInfo::class.java,
                PersistentKey::class.java
        )
) {
    override val migrationResource = "member-info.changelog-master"

    @Entity
    @Table(name = "member_info")
    @Suppress("LongParameterList")
    class PersistentMemberInfo(
            @Id
            @Column(name = "member_id", nullable = false)
            val memberId: String,

            @Column(name = "party_name", nullable = false)
            val partyName: String,

            @Column(name = "public_key_hash", nullable = false)
            val publicKeyHash: String,

            @Column(name = "keys", nullable = false)
            @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
            @JoinColumn(name = "member_id", foreignKey = ForeignKey(name = "FK__member_info_key"))
            val keys: Set<PersistentKey>,

            @Column(name = "endpoints", nullable = false)
            @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
            @JoinColumn(name = "member_id", foreignKey = ForeignKey(name = "FK__member_info_endpoint"))
            val endpoints: Set<PersistentEndpointInfo>,

            @Column(name = "status", nullable = false)
            val status: MemberStatus,

            @Column(name = "software_version", nullable = false)
            val softwareVersion: String,

            @Column(name = "platform_version", nullable = false)
            val platformVersion: Int,

            @Column(name = "role", nullable = false)
            val role: MemberRole,

            @Column(name = "properties", nullable = false)
            @Lob
            val properties: ByteArray
    ) {
        companion object {
            fun fromMemberInfo(memberInfo: MemberInfo) = with(memberInfo) {
                PersistentMemberInfo(
                        memberId = memberId,
                        partyName = party.name.toString(),
                        publicKeyHash = party.owningKey.toStringShort(),
                        keys = keys.map { PersistentKey.fromPublicKey(it) }.toSet(),
                        endpoints = endpoints.map { PersistentEndpointInfo.fromEndpointInfo(it) }.toSet(),
                        status = status,
                        softwareVersion = softwareVersion,
                        platformVersion = platformVersion,
                        role = role,
                        properties = properties.serialize().bytes
                )
            }
        }

        private fun toParty(): Party {
            val name = CordaX500Name.parse(partyName)
            val publicKey = keys.single { it.publicKeyHash == publicKeyHash }.toPublicKey()
            return Party(name, publicKey)
        }

        fun toMemberInfo() = MemberInfo(
                memberId = memberId,
                party = toParty(),
                keys = keys.map { it.toPublicKey() },
                endpoints = endpoints.map { it.toEndpointInfo() },
                status = status,
                softwareVersion = softwareVersion,
                platformVersion = platformVersion,
                role = role,
                properties = properties.deserialize()
        )
    }

    @Entity
    @Table(name = "member_info_endpoint")
    class PersistentEndpointInfo(
            @Id
            @GeneratedValue
            @Column(name = "endpoint_id", nullable = false)
            val id: Int,

            @Column(name = "connection_url", nullable = false)
            val connectionURL: String,

            @Column(name = "tls_subject_name", nullable = false)
            val tlsSubjectName: String,

            @Column(name = "protocol_version", nullable = false)
            val protocolVersion: Int
    ) {
        companion object {
            fun fromEndpointInfo(endpointInfo: EndpointInfo) = with(endpointInfo) {
                PersistentEndpointInfo(
                        id = 0,
                        connectionURL = connectionURL,
                        tlsSubjectName = tlsSubjectName.name,
                        protocolVersion = protocolVersion
                )
            }
        }

        fun toEndpointInfo() = EndpointInfo(
                connectionURL = connectionURL,
                tlsSubjectName = X500Principal(tlsSubjectName),
                protocolVersion = protocolVersion
        )
    }

    @Entity
    @Table(name = "member_info_key")
    class PersistentKey(
            @Id
            @Column(name = "public_key_hash", nullable = false)
            val publicKeyHash: String,

            @Lob
            @Column(name = "public_key", nullable = false)
            val publicKey: ByteArray
    ) {
        companion object {
            fun fromPublicKey(publicKey: PublicKey) = PersistentKey(publicKey.toStringShort(), publicKey.encoded)
        }

        fun toPublicKey() = Crypto.decodePublicKey(publicKey)
    }
}