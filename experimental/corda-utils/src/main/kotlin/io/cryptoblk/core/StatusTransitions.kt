package io.cryptoblk.core

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import kotlin.reflect.KClass

/**
 * Contract state that records changes of some [status] on the ledger and roles of parties that are participants
 * in that state using [roleToParty].
 */
interface StatusTrackingContractState<out S, in R> : ContractState {
    val status: S
    fun roleToParty(role: R): Party
}

/**
 * Definition of finite state transition: for a particular command in a TX, it defines what transitions can be done
 * [from] what status [to] what statuses, and who needs to sign them ([signer]).
 * If [from] is null, it means there doesn't need to be any input; if [to] is null, it mean there doesn't need to be any output.
 * If [signer] is null, it means anyone can sign it.
 */
data class TransitionDef<out S, out R>(val cmd: Class<*>, val signer: R?, val from: S?, val to: List<S?>)

/**
 * Holds visualized PUML graph in [printedPUML] and the relevant state class name in [stateClassName].
 */
data class PrintedTransitionGraph(val stateClassName: String, val printedPUML: String)

/**
 * Shorthand for defining transitions directly from the command class
 */
fun <S, R> CommandData.txDef(signer: R? = null, from: S?, to: List<S?>):
    TransitionDef<S, R> = TransitionDef(this::class.java, signer, from, to)

/**
 * For a given [stateClass] that tracks a status, it holds all possible transitions in [ts].
 * This can be used for generic [verify] in contract code as well as for visualizing the state transition graph in PUML ([printGraph]).
 */
class StatusTransitions<out S, in R, T : StatusTrackingContractState<S, R>>(private val stateClass: KClass<T>,
    private vararg val ts: TransitionDef<S, R>) {

    private val allowedCmds = ts.map { it.cmd }.toSet()

    private fun matchingTransitions(input: S?, output: S?, command: CommandData): List<TransitionDef<S, R>> {
        val options = ts.filter {
            (it.from == input) && (output in it.to) && (it.cmd == command.javaClass)
        }
        if (options.isEmpty()) throw IllegalStateException("Transition [$input -(${command.javaClass.simpleName})-> $output] not allowed")
        return options
    }

    /**
     * Generic verification based on provided [TransitionDef]s
     */
    fun verify(tx: LedgerTransaction) {
        val relevantCmds = tx.commands.filter { allowedCmds.contains(it.value.javaClass) }
        require(relevantCmds.isNotEmpty()) { "Transaction must have at least one Command relevant to its defined transitions" }

        relevantCmds.forEach { cmd ->
            val ins = tx.inputsOfType(stateClass.java)
            val inputStates = if (ins.isEmpty()) listOf(null) else ins
            val outs = tx.outputsOfType(stateClass.java)
            val outputStates = if (outs.isEmpty()) listOf(null) else outs

            // for each combination of in x out which should normally be at most 1...
            inputStates.forEach { inp ->
                outputStates.forEach { outp ->
                    assert((inp != null) || (outp != null))
                    val options = matchingTransitions(inp?.status, outp?.status, cmd.value)

                    val signerGroup = options.groupBy { it.signer }.entries.singleOrNull()
                        ?: throw IllegalStateException("Cannot have different signers in StatusTransitions for the same command.")
                    val signer = signerGroup.key
                    if (signer != null) {
                        // which state determines who is the signer? by default the input, unless it's the initial transition
                        val state = (inp ?: outp)!!
                        val signerParty = state.roleToParty(signer)
                        if (!cmd.signers.contains(signerParty.owningKey))
                            throw IllegalStateException("Command ${cmd.value.javaClass} must be signed by $signer")
                    }
                }
            }
        }
    }

    fun printGraph(): PrintedTransitionGraph {
        val sb = StringBuilder()
        sb.append("@startuml\n")
        if (stateClass.simpleName != null) sb.append("title ${stateClass.simpleName}\n")
        ts.forEach { txDef ->
            val fromStatus = txDef.from?.toString() ?: "[*]"
            txDef.to.forEach { to ->
                val toStatus = (to ?: "[*]").toString()
                val cmd = txDef.cmd.simpleName
                val signer = txDef.signer?.toString() ?: "anyone involved"

                sb.append("$fromStatus --> $toStatus : $cmd (by $signer)\n")
            }
        }
        sb.append("@enduml")
        return PrintedTransitionGraph(stateClass.simpleName ?: "", sb.toString())
    }
}