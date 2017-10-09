package net.corda.node.services.vault

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import org.hibernate.annotations.Generated
import org.hibernate.annotations.GenerationTime
import java.io.Serializable
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
    @Table(name = "vault_states",
            indexes = arrayOf(Index(name = "state_status_idx", columnList = "state_status")))
    class VaultStates(
            /** refers to the X500Name of the notary a state is attached to */
            @Column(name = "notary_name")
            var notary: AbstractParty,

            /** references a concrete ContractState that is [QueryableState] and has a [MappedSchema] */
            @Column(name = "contract_state_class_name")
            var contractStateClassName: String,

            /** refers to serialized transaction Contract State */
            @Lob
            @Column(name = "contract_state")
            var contractState: ByteArray,

            /** state lifecycle: unconsumed, consumed */
            @Column(name = "state_status")
            var stateStatus: Vault.StateStatus,

            /** refers to timestamp recorded upon entering UNCONSUMED state */
            @Column(name = "recorded_timestamp")
            var recordedTime: Instant,

            /** refers to timestamp recorded upon entering CONSUMED state */
            @Column(name = "consumed_timestamp", nullable = true)
            var consumedTime: Instant? = null,

            /** used to denote a state has been soft locked (to prevent double spend)
             *  will contain a temporary unique [UUID] obtained from a flow session */
            @Column(name = "lock_id", nullable = true)
            var lockId: String? = null,

            /** refers to the last time a lock was taken (reserved) or updated (released, re-reserved) */
            @Column(name = "lock_timestamp", nullable = true)
            var lockUpdateTime: Instant? = null
    ) : PersistentState()

    @Entity
    @Table(name = "vault_linear_states",
            indexes = arrayOf(Index(name = "external_id_index", columnList = "external_id"),
                    Index(name = "uuid_index", columnList = "uuid")))
    class VaultLinearStates(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            @Column(name = "participants")
            var participants: MutableSet<AbstractParty>? = null,
            // Reason for not using Set is described here:
            // https://stackoverflow.com/questions/44213074/kotlin-collection-has-neither-generic-type-or-onetomany-targetentity

            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
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
            @Column(name = "participants")
            var participants: MutableSet<AbstractParty>? = null,

            /** [OwnableState] attributes */

            /** X500Name of owner party **/
            @Column(name = "owner_name")
            var owner: AbstractParty,

            /** [FungibleAsset] attributes
             *
             *  Note: the underlying Product being issued must be modelled into the
             *  custom contract itself (eg. see currency in Cash contract state)
             */

            /** Amount attributes */
            @Column(name = "quantity")
            var quantity: Long,

            /** Issuer attributes */

            /** X500Name of issuer party **/
            @Column(name = "issuer_name")
            var issuer: AbstractParty,

            @Column(name = "issuer_reference")
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
    @Table(name = "vault_transaction_notes",
            indexes = arrayOf(Index(name = "seq_no_index", columnList = "seq_no"),
                    Index(name = "transaction_id_index", columnList = "transaction_id")))
    class VaultTxnNote(
            @Id
            @GeneratedValue
            @Column(name = "seq_no")
            var seqNo: Int,

            @Column(name = "transaction_id", length = 64)
            var txId: String,

            @Column(name = "note")
            var note: String
    ) : Serializable {
        constructor(txId: String, note: String) : this(0, txId, note)
    }
}