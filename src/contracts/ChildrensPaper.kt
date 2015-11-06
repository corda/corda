package contracts

import core.*
import java.security.PublicKey
import java.time.Instant

/**
 * "Children's paper" is basically like commercial paper, but modelled by a non-expert and without any attention paid
 * to the issuance aspect. It has a weird name to emphasise that it's not a prototype of real CP, as that's currently
 * waiting for Jo/Ayoub to deliver full CP use case models. This file may be renamed later if it grows up and
 * becomes real CP.
 *
 * Open issues:
 *  - In this model, you cannot merge or split CP. Can you do this normally? We could model CP as a specialised form
 *    of cash, or reuse some of the cash code?
 *  - Currently cannot trade more than one piece of CP in a single transaction. This is probably going to be a common
 *    issue: need to find a cleaner way to allow this. Does the single-execution-per-transaction model make sense?
 */

// TODO: This is very incomplete! Do not attempt to find mistakes in it just yet.

val CP_PROGRAM_ID = SecureHash.sha256("childrens-paper")

data class ChildrensPaperState(
    val issuance: InstitutionReference,
    val owner: PublicKey,
    val faceValue: Amount,
    val maturityDate: Instant
) : ContractState {
    override val programRef = CP_PROGRAM_ID

    fun withoutOwner() = copy(owner = NullPublicKey)
}

// TODO: Generalise the notion of an owned object into a superclass/supercontract. Consider composition vs inheritance.
sealed class CPCommands : Command {
    class MoveCommand : CPCommands()
    class RedeemCommand : CPCommands()
}

object ChildrensPaper : Contract {
    override fun verify(inStates: List<ContractState>, outStates: List<ContractState>, args: List<VerifiedSigned<Command>>, time: Instant) {
        // There are two possible things that can be done with CP. The first is trading it. The second is redeeming it
        // for cash on or after the maturity date.
        val command = args.requireSingleCommand<CPCommands>()

        // For now do not allow multiple pieces of CP to trade in a single transaction. Study this more!
        val input = inStates.filterIsInstance<ChildrensPaperState>().single()
        val output = outStates.filterIsInstance<ChildrensPaperState>().single()

        when (command.value) {
            is CPCommands.MoveCommand -> requireThat {
                "the transaction is signed by the owner of the CP" by (command.signer == input.owner)
                "the output state is the same as the input state except for owner" by (input.withoutOwner() == output.withoutOwner())
            }

            is CPCommands.RedeemCommand -> {
                // TODO: Lots more needed here: the CP state must be destroyed by this transaction, command.signer is not really right etc
                val received = outStates.sumCashBy(command.signer)
                requireThat {
                    "the paper must have matured" by (input.maturityDate < time)
                    "the received amount equals the face value" by (received == input.faceValue)
                }
            }
        }
    }
}

