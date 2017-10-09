package net.corda.core.schemas

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.util.*
import javax.persistence.*

/**
 * JPA representation of the common schema entities
 */
object CommonSchema

/**
 * First version of the Vault ORM schema
 */
object CommonSchemaV1 : MappedSchema(schemaFamily = CommonSchema.javaClass, version = 1, mappedTypes = emptyList()) {

    @MappedSuperclass
    open class LinearState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            @Column(name = "participants")
            var participants: MutableSet<AbstractParty>? = null,

            /**
             *  Represents a [LinearState] [UniqueIdentifier]
             */
            @Column(name = "external_id")
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            var uuid: UUID

    ) : PersistentState() {
        constructor(uid: UniqueIdentifier, _participants: Set<AbstractParty>)
                : this(participants = _participants.toMutableSet(),
                externalId = uid.externalId,
                uuid = uid.id)
    }

    @MappedSuperclass
    open class FungibleState(
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
    ) : PersistentState()
}