package net.corda.notary.standalonejpa

import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.Type
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "notary_request_log")
@CordaSerializable
class Request(
        @Id
        @Column(nullable = true, length = 76)
        var id: String? = null,

        @Column(name = "consuming_transaction_id", nullable = true, length = 64)
        val consumingTxHash: String?,

        @Column(name = "requesting_party_name", nullable = true, length = 255)
        var partyName: String?,

        @Type(type="org.hibernate.type.BinaryType")
        @Column(name = "request_signature", nullable = false, length = 1024)
        val requestSignature: ByteArray,

        @Column(name = "request_timestamp", nullable = false)
        var requestDate: Instant
)

@Entity
@Table(name = "notary_committed_states")
@NamedQuery(name = "CommittedState.select", query = "SELECT c from CommittedState c WHERE c.id in :ids")
class CommittedState(
        @Id
        @Column(name = "state_ref", length = 73)
        val id: String,
        @Column(name = "consuming_transaction_id", nullable = false, length = 64)
        val consumingTxHash: String)

@Entity
@Table(name = "notary_committed_transactions")
class CommittedTransaction(
        @Id
        @Column(name = "transaction_id", nullable = false, length = 64)
        val transactionId: String
)

object JPANotarySchema

object JPANotarySchemaV1 : MappedSchema(schemaFamily = JPANotarySchema.javaClass, version = 1,
        mappedTypes = listOf(CommittedTransaction::class.java,
                Request::class.java,
                CommittedState::class.java
        )) {
    override val migrationResource = "notary-sql.changelog-master"
}