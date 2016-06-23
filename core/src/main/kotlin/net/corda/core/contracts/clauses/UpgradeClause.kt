package net.corda.core.contracts.clauses

import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import java.security.PublicKey

/**
 * Verify that the upgrade of an input state is correctly signed, and the output state matches. This requires that all
 * participants of the inputs have signed the upgrade transaction (to confirm they're happy with the replacement),
 * and that the outputs correspond 1:1 with the inputs.
 */
abstract class UpgradeClause<S: ContractState, C: CommandData, K: Any> : Clause<S, C, K>() {
    abstract val expectedType: Class<*>
    override fun verify(tx: TransactionForContract, inputs: List<S>, outputs: List<S>, commands: List<AuthenticatedObject<C>>, groupingKey: K?): Set<C> {
        val matchedCommands = commands.filter { it.value is UpgradeCommand<*> }
        val command = matchedCommands.select<UpgradeCommand<S>>().singleOrNull()

        if (command != null) {
            // Now check the digital signatures on the move command. Every input has an owning public key, and we must
            // see a signature from each of those keys. The actual signatures have been verified against the transaction
            // data by the platform before execution.
            val participants: Set<CompositeKey> = inputs.flatMap { it.participants }.toSet()
            val keysThatSigned: Set<CompositeKey> = command.signers.toSet()
            requireThat {
                "the signing keys include all participant keys" by keysThatSigned.containsAll(participants)
                "there is at least one input" by inputs.isNotEmpty()
                "number of inputs and outputs match" by (inputs.size == outputs.size)
                "all inputs belong to the legacy contract" by inputs.all { it.contract == command.value.oldContract }
                "all inputs are of a suitable type" by outputs.all { it.javaClass == expectedType }
                "all outputs belong to the upgraded contract" by outputs.all { it.contract == command.value.newContract }
            }

            val upgradeContract = command.value.newContract

            for (stateIdx in 0..inputs.size) {
                val expected: ContractState = upgradeContract.upgrade(inputs[stateIdx] as S)
                require(outputs[stateIdx] == expected) { "output state is must be an upgraded version of the input state" }
            }
        }

        return matchedCommands.map { it.value }.toSet()
    }
}
