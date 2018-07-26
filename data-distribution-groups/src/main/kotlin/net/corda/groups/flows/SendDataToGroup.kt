package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.groups.contracts.Group
import java.security.PublicKey

// TODO: Persist the txids sent to the group via the "GroupService".
@InitiatingFlow
class SendDataToGroup(val key: PublicKey, val transaction: SignedTransaction, val sender: Party? = null) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Grab all the group states.
        // Do all this in memory for the time being.
        // TODO: Deal with paging when the need arises.
        val query = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val allGroupStates = serviceHub.vaultService.queryBy<Group.State>(query).states.map { it.state.data }

        // Filter out the group states for the specified key and get all the participants.
        // TODO: Make sure of the database for querying instead of doing it in memory.
        val groupStatesForKey = allGroupStates.filter { it.details.key == key }
        val participantsForGroup = groupStatesForKey.flatMap(Group.State::participants).toSet()

        // Neighbours are the union of all participants
        // across all group states with the specified key.
        val groupNeighbours = if (sender != null) {
            // Remove the party which just sent us the transaction
            // otherwise we'll end up in a never ending loop.
            participantsForGroup - ourIdentity - sender
        } else {
            participantsForGroup - ourIdentity
        }

        // This node didn't invite anyone to join the group.
        if (groupNeighbours.isEmpty()) {
            logger.info("Parties to send the transaction to.")
            return
        }

        logger.info("Sending transaction ${transaction.id} to parties $groupNeighbours in group $key.")

        // Create sessions for each neighbour.
        // Send all the transactions to each party.
        groupNeighbours.forEach { party ->
            val session = initiateFlow(party)
            // Send the group key first so the recipient knows which signature
            // to check on the transaction they are about to receive.
            logger.info("Sending transaction ${transaction.id} to party $party in group $key.")
            // TODO: With a custom "send tx to group flow" we won't need to send the key + cert separately.
            // Although only the key is sent at this time.
            session.send(key)
            subFlow(SendTransactionFlow(session, transaction))
        }
    }
}