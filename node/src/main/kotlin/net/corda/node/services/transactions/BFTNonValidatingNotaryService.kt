package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.TimestampChecker
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.flows.NotaryException
import net.corda.node.services.api.ServiceHubInternal
import org.jetbrains.exposed.sql.Database
import kotlin.concurrent.thread

/**
 * A non-validating notary service operated by a group of parties that don't necessarily trust each other.
 *
 * A transaction is notarised when the consensus is reached by the cluster on its uniqueness, and timestamp validity.
 */
class BFTNonValidatingNotaryService(services: ServiceHubInternal,
                                    timestampChecker: TimestampChecker,
                                    serverId: Int,
                                    db: Database,
                                    val client: BFTSMaRt.Client) : NotaryService(services) {
    init {
        thread(name = "BFTSmartServer-$serverId", isDaemon = true) {
            Server(serverId, db, "bft_smart_notary_committed_states", services, timestampChecker)
        }
    }

    companion object {
        val type = SimpleNotaryService.type.getSubType("bft")
        private val log = loggerFor<BFTNonValidatingNotaryService>()
    }

    override fun createFlow(otherParty: Party) = ServiceFlow(otherParty, client)

    class ServiceFlow(val otherSide: Party, val client: BFTSMaRt.Client) : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? {
            val stx = receive<FilteredTransaction>(otherSide).unwrap { it }
            val signatures = commit(stx)
            send(otherSide, signatures)
            return null
        }

        private fun commit(stx: FilteredTransaction): List<DigitalSignature> {
            val response = client.commitTransaction(stx, otherSide)
            when (response) {
                is BFTSMaRt.ClusterResponse.Error -> throw NotaryException(response.error)
                is BFTSMaRt.ClusterResponse.Signatures -> {
                    log.debug("All input states of transaction ${stx.rootHash} have been committed")
                    return response.txSignatures
                }
            }
        }
    }

    class Server(id: Int,
                 db: Database,
                 tableName: String,
                 services: ServiceHubInternal,
                 timestampChecker: TimestampChecker) : BFTSMaRt.Server(id, db, tableName, services, timestampChecker) {

        override fun executeCommand(command: ByteArray): ByteArray {
            val request = command.deserialize<BFTSMaRt.CommitRequest>()
            val ftx = request.tx as FilteredTransaction
            val response = verifyAndCommitTx(ftx, request.callerIdentity)
            return response.serialize().bytes
        }

        fun verifyAndCommitTx(ftx: FilteredTransaction, callerIdentity: Party): BFTSMaRt.ReplicaResponse {
            return try {
                val id = ftx.rootHash
                val inputs = ftx.filteredLeaves.inputs

                validateTimestamp(ftx.filteredLeaves.timestamp)
                commitInputStates(inputs, id, callerIdentity)

                log.debug { "Inputs committed successfully, signing $id" }
                val sig = sign(id.bytes)
                BFTSMaRt.ReplicaResponse.Signature(sig)
            } catch (e: NotaryException) {
                log.debug { "Error processing transaction: ${e.error}" }
                BFTSMaRt.ReplicaResponse.Error(e.error)
            }
        }
    }
}
