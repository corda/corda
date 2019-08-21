package net.corda.node.services.persistence

import net.corda.core.crypto.toStringShort
import org.hibernate.annotations.Type
import java.security.PublicKey
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "pk_hash_to_ext_id_map", indexes = [Index(name = "pk_hash_to_xid_idx", columnList = "public_key_hash")])
class PublicKeyHashToExternalId(
        @Id
        @GeneratedValue
        @Column(name = "id", unique = true, nullable = false)
        val key: Long?,

        @Column(name = "external_id", nullable = false)
        @Type(type = "uuid-char")
        val externalId: UUID,

        @Column(name = "public_key_hash", nullable = false)
        val publicKeyHash: String
) {
    constructor(accountId: UUID, publicKey: PublicKey)
            : this(null, accountId, publicKey.toStringShort())
}