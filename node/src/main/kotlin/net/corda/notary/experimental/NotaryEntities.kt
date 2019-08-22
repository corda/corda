package net.corda.notary.experimental

import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.time.Instant
import javax.persistence.*

object NotaryEntities {
    @MappedSuperclass
    class BaseComittedState(
            @EmbeddedId
            val id: PersistentStateRef,

            @Column(name = "consuming_transaction_id", nullable = true)
            val consumingTxHash: String?
    )

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}notary_request_log")
    @CordaSerializable
    class Request(
            @Id
            @GeneratedValue
            @Column(nullable = true)
            val id: Int? = null,

            @Column(name = "consuming_transaction_id", nullable = true)
            val consumingTxHash: String?,

            @Column(name = "requesting_party_name", nullable = true)
            var partyName: String?,

            @Lob
            @Column(name = "request_signature", nullable = false)
            val requestSignature: ByteArray,

            @Column(name = "request_timestamp", nullable = false)
            var requestDate: Instant
    )
}