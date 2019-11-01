package net.corda.node.djvm

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.internal.lazyMapped
import java.security.PublicKey
import java.util.function.Function

class CommandBuilder : Function<Array<Any?>, List<CommandWithParties<CommandData>>> {
    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<Any?>): List<CommandWithParties<CommandData>> {
        val signers = inputs[0] as? List<List<PublicKey>> ?: emptyList()
        val commandsData = inputs[1] as? List<CommandData> ?: emptyList()
        val partialMerkleLeafIndices = inputs[2] as? IntArray

        /**
         * This logic has been lovingly reproduced from [net.corda.core.internal.deserialiseCommands].
         */
        return if (partialMerkleLeafIndices != null) {
            check(commandsData.size <= signers.size) {
                "Invalid Transaction. Fewer Signers (${signers.size}) than CommandData (${commandsData.size}) objects"
            }
            if (partialMerkleLeafIndices.isNotEmpty()) {
                check(partialMerkleLeafIndices.max()!! < signers.size) {
                    "Invalid Transaction. A command with no corresponding signer detected"
                }
            }
            commandsData.lazyMapped { commandData, index ->
                CommandWithParties(signers[partialMerkleLeafIndices[index]], emptyList(), commandData)
            }
        } else {
            check(commandsData.size == signers.size) {
                "Invalid Transaction. Sizes of CommandData (${commandsData.size}) and Signers (${signers.size}) do not match"
            }
            commandsData.lazyMapped { commandData, index ->
                CommandWithParties(signers[index], emptyList(), commandData)
            }
        }
    }
}
