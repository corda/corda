package net.corda.groups.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.groups.schemas.GroupSchemaV1
import java.security.PublicKey

/** Just a stub for now. */
// TODO: Update and add KDocs.
class Group : Contract {
    companion object {
        @JvmStatic
        val contractId = "net.corda.groups.contracts.Group"
    }

    class Create : CommandData
    class Invite : CommandData

    override fun verify(tx: LedgerTransaction) = Unit

    @CordaSerializable
    data class Details(val key: PublicKey, val name: String)

    @CordaSerializable
    data class DetailsWithCert(val details: Details, val cert: PartyAndCertificate)

    data class State(val details: Details, override val participants: List<Party>) : ContractState, QueryableState {
        @Suppress("Unused")
        constructor(key: PublicKey, name: String) : this(Details(key, name), listOf())

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(GroupSchemaV1)
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is GroupSchemaV1 -> GroupSchemaV1.PersistentGroupState(
                        key = this.details.key.encoded,
                        name = this.details.name
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }
}