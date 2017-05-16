package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.flows.NotaryException
import net.corda.node.services.api.ServiceHubInternal
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * A non-validating notary service operated by a group of parties that don't necessarily trust each other.
 *
 * A transaction is notarised when the consensus is reached by the cluster on its uniqueness, and time-window validity.
 */
class BFTNonValidatingNotaryService(config: BFTSMaRtConfig,
                                    services: ServiceHubInternal,
                                    timeWindowChecker: TimeWindowChecker,
                                    serverId: Int,
                                    db: Database,
                                    private val client: BFTSMaRt.Client) : NotaryService {
    init {
        val configHandle = config.handle()
        thread(name = "BFTSmartServer-$serverId", isDaemon = true) {
            configHandle.use {
                Server(configHandle.path, serverId, db, "bft_smart_notary_committed_states", services, timeWindowChecker)
            }
        }
    }

    companion object {
        val type = SimpleNotaryService.type.getSubType("bft")
        private val log = loggerFor<BFTNonValidatingNotaryService>()
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        ServiceFlow(otherParty, client)
    }

    private class ServiceFlow(val otherSide: Party, val client: BFTSMaRt.Client) : FlowLogic<Void?>() {
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

    private class Server(configHome: Path,
                         id: Int,
                         db: Database,
                         tableName: String,
                         services: ServiceHubInternal,
                         timeWindowChecker: TimeWindowChecker) : BFTSMaRt.Server(configHome, id, db, tableName, services, timeWindowChecker) {

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

                validateTimeWindow(ftx.filteredLeaves.timeWindow)
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
