package net.corda.groups.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class Group : Contract {
    companion object {
        @JvmStatic
        val contractId = "net.corda.groups.contracts.Group"
    }

    class Create : CommandData
    class Invite : CommandData

    override fun verify(tx: LedgerTransaction) = Unit

    @CordaSerializable
    data class Details(val name: String, val key: PublicKey)

    @CordaSerializable
    data class DetailsWithCert(val details: Details, val cert: PartyAndCertificate)

    data class State(val details: Details, override val participants: List<Party>) : ContractState
}