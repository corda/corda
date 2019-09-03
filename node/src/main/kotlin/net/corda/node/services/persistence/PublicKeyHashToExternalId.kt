package net.corda.node.services.persistence

import net.corda.core.crypto.toStringShort
import org.hibernate.annotations.Type
import java.security.PublicKey
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "pk_hash_to_ext_id_map", indexes = [
    Index(name = "ext_id_idx", columnList = "external_id")
])
class PublicKeyHashToExternalId(
        @Column(name = "external_id", nullable = false)
        @Type(type = "uuid-char")
        val externalId: UUID,

        @Id
        @Column(name = "public_key_hash", nullable = false)
        val publicKeyHash: String

) {
    constructor(accountId: UUID, publicKey: PublicKey)
            : this(accountId, publicKey.toStringShort())
}