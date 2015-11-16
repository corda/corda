package contracts

import core.*
import java.security.PublicKey
import java.time.Instant

/**
 * This is an ultra-trivial implementation of commercial paper, which is essentially a simpler version of a corporate
 * bond. It can be seen as a company-specific currency. A company issues CP with a particular face value, like $100
 * but sells it for less, say $90. The paper can be redeemed for cash at a given date in the future. Thus this example
 * would have a 10% interest rate with a single repayment. Commercial paper is often rolled out (redeemed and the
 * money used to immediately rebuy).
 *
 * This contract is not intended to realistically model CP. It is here only to act as a next step up above cash in
 * the prototyping phase. It is thus very incomplete.
 *
 * Open issues:
 *  - In this model, you cannot merge or split CP. Can you do this normally? We could model CP as a specialised form
 *    of cash, or reuse some of the cash code?
 *  - Currently cannot trade more than one piece of CP in a single transaction. This is probably going to be a common
 *    issue: need to find a cleaner way to allow this. Does the single-execution-per-transaction model make sense?
 */

val CP_PROGRAM_ID = SecureHash.sha256("replace-me-later-with-bytecode-hash")

// TODO: Generalise the notion of an owned instrument into a superclass/supercontract. Consider composition vs inheritance.
object CommercialPaper : Contract {
    override val legalContractReference: String = "https://en.wikipedia.org/wiki/Commercial_paper"

    data class State(
        val issuance: InstitutionReference,
        val owner: PublicKey,
        val faceValue: Amount,
        val maturityDate: Instant
    ) : ContractState {
        override val programRef = CP_PROGRAM_ID

        fun withoutOwner() = copy(owner = NullPublicKey)
    }

    sealed class Commands : Command {
        object Move : Commands()
        object Redeem : Commands()
    }

    override fun verify(tx: TransactionForVerification) {
        with(tx) {
            // There are two possible things that can be done with CP. The first is trading it. The second is redeeming it
            // for cash on or after the maturity date.
            val command = args.requireSingleCommand<CommercialPaper.Commands>()

            // For now do not allow multiple pieces of CP to trade in a single transaction. Study this more!
            val input = inStates.filterIsInstance<CommercialPaper.State>().single()

            requireThat {
                "the transaction is signed by the owner of the CP" by (command.signers.contains(input.owner))
            }

            when (command.value) {
                is Commands.Move -> requireThat {
                    val output = outStates.filterIsInstance<CommercialPaper.State>().single()
                    "the output state is the same as the input state except for owner" by (input.withoutOwner() == output.withoutOwner())
                }

                is Commands.Redeem -> requireThat {
                    val received = outStates.sumCashOrNull() ?: throw IllegalStateException("no cash being redeemed")
                    // Do we need to check the signature of the issuer here too?
                    "the paper must have matured" by (input.maturityDate < time)
                    "the received amount equals the face value" by (received == input.faceValue)
                    "the paper must be destroyed" by outStates.filterIsInstance<CommercialPaper.State>().none()
                }
            }
        }
    }
}

