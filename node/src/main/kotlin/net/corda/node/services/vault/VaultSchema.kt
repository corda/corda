package net.corda.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.*
import javax.persistence.*

/**
 * JPA representation of the core Vault Schema
 */
object VaultSchema

/**
 * First version of the Vault ORM schema
 */
@CordaSerializable
object VaultSchemaV1 : MappedSchema(schemaFamily = VaultSchema.javaClass, version = 1,
        mappedTypes = listOf(VaultStates::class.java, VaultLinearStates::class.java, VaultFungibleStates::class.java, VaultTxnNote::class.java)) {
    @Entity
    @Table(name = "vault_states", indexes = [Index(name = "state_status_idx", columnList = "state_status"), Index(name = "lock_id_idx", columnList = "lock_id, state_status")])
    class VaultStates(
            /** NOTE: serialized transaction state (including contract state) is now resolved from transaction store */
            // TODO: create a distinct table to hold serialized state data (once DBTransactionStore is encrypted)

            /** refers to the X500Name of the notary a state is attached to */
            @Column(name = "notary_name", nullable = false)
            var notary: Party,

            /** references a concrete ContractState that is [QueryableState] and has a [MappedSchema] */
            @Column(name = "contract_state_class_name", nullable = false)
            var contractStateClassName: String,

            /** state lifecycle: unconsumed, consumed */
            @Column(name = "state_status", nullable = false)
            var stateStatus: Vault.StateStatus,

            /** refers to timestamp recorded upon entering UNCONSUMED state */
            @Column(name = "recorded_timestamp", nullable = false)
            var recordedTime: Instant,

            /** refers to timestamp recorded upon entering CONSUMED state */
            @Column(name = "consumed_timestamp", nullable = true)
            var consumedTime: Instant? = null,

            /** used to denote a state has been soft locked (to prevent double spend)
             *  will contain a temporary unique [UUID] obtained from a flow session */
            @Column(name = "lock_id", nullable = true)
            var lockId: String? = null,

            /** Used to determine whether a state is modifiable by the recording node */
            @Column(name = "is_relevant", nullable = false)
            var isRelevant: Vault.StateRelevance,

            /** refers to the last time a lock was taken (reserved) or updated (released, re-reserved) */
            @Column(name = "lock_timestamp", nullable = true)
            var lockUpdateTime: Instant? = null
    ) : PersistentState()

    @Entity
    @Table(name = "vault_linear_states", indexes = [Index(name = "external_id_index", columnList = "external_id"), Index(name = "uuid_index", columnList = "uuid")])
    class VaultLinearStates(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            @CollectionTable(name = "vault_linear_states_parts",
                    joinColumns = [(JoinColumn(name = "output_index", referencedColumnName = "output_index")), (JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"))],
                    foreignKey = ForeignKey(name = "FK__lin_stat_parts__lin_stat"))
            @Column(name = "participants")
            var participants: MutableSet<AbstractParty?>? = null,
            // Reason for not using Set is described here:
            // https://stackoverflow.com/questions/44213074/kotlin-collection-has-neither-generic-type-or-onetomany-targetentity

            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id", nullable = true)
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            @Type(type = "uuid-char")
            var uuid: UUID
    ) : PersistentState() {
        constructor(uid: UniqueIdentifier, _participants: List<AbstractParty>) :
                this(externalId = uid.externalId,
                        uuid = uid.id,
                        participants = _participants.toMutableSet())
    }

    @Entity
    @Table(name = "vault_fungible_states")
    class VaultFungibleStates(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            @CollectionTable(name = "vault_fungible_states_parts",
                    joinColumns = [(JoinColumn(name = "output_index", referencedColumnName = "output_index")), (JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"))],
                    foreignKey = ForeignKey(name = "FK__fung_st_parts__fung_st"))
            @Column(name = "participants", nullable = true)
            var participants: MutableSet<AbstractParty>? = null,

            /** [OwnableState] attributes */

            /** X500Name of owner party **/
            @Column(name = "owner_name", nullable = true)
            var owner: AbstractParty?,

            /** [FungibleAsset] attributes
             *
             *  Note: the underlying Product being issued must be modelled into the
             *  custom contract itself (eg. see currency in Cash contract state)
             */

            /** Amount attributes */
            @Column(name = "quantity", nullable = false)
            var quantity: Long,

            /** Issuer attributes */

            /** X500Name of issuer party **/
            @Column(name = "issuer_name", nullable = true)
            var issuer: AbstractParty?,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var issuerRef: ByteArray
    ) : PersistentState() {
        constructor(_owner: AbstractParty, _quantity: Long, _issuerParty: AbstractParty, _issuerRef: OpaqueBytes, _participants: List<AbstractParty>) :
                this(owner = _owner,
                        quantity = _quantity,
                        issuer = _issuerParty,
                        issuerRef = _issuerRef.bytes,
                        participants = _participants.toMutableSet())
    }

    @Entity
    @Table(name = "vault_transaction_notes", indexes = [Index(name = "seq_no_index", columnList = "seq_no"), Index(name = "transaction_id_index", columnList = "transaction_id")])
    class VaultTxnNote(
            @Id
            @GeneratedValue
            @Column(name = "seq_no", nullable = false)
            var seqNo: Int,

            @Column(name = "transaction_id", length = 64, nullable = true)
            var txId: String?,

            @Column(name = "note", nullable = true)
            var note: String?
    ) {
        constructor(txId: String, note: String) : this(0, txId, note)
    }
}