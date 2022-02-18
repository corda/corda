package net.corda.node.djvm

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.internal.lazyMapped
import java.security.PublicKey
import java.util.function.Function
import java.util.function.Supplier

class CommandBuilder : Function<Array<Any?>, Supplier<List<CommandWithParties<CommandData>>>> {
    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<Any?>): Supplier<List<CommandWithParties<CommandData>>> {
        val signersProvider = inputs[0] as? Supplier<List<List<PublicKey>>> ?: Supplier(::emptyList)
        val commandsDataProvider = inputs[1] as? Supplier<List<CommandData>> ?: Supplier(::emptyList)
        val partialMerkleLeafIndices = inputs[2] as? IntArray

        /**
         * This logic has been lovingly reproduced from [net.corda.core.internal.deserialiseCommands].
         */
        return Supplier {
            val signers = signersProvider.get()
            val commandsData = commandsDataProvider.get()

            if (partialMerkleLeafIndices != null) {
                check(commandsData.size <= signers.size) {
                    "Invalid Transaction. Fewer Signers (${signers.size}) than CommandData (${commandsData.size}) objects"
                }
                if (partialMerkleLeafIndices.isNotEmpty()) {
                    check(partialMerkleLeafIndices.max()!! < signers.size) {
                        "Invalid Transaction. A command with no corresponding signer detected"
                    }
                }
                commandsData.lazyMapped { commandData, index ->
                    // Deprecated signingParties property not supported.
                    CommandWithParties(signers[partialMerkleLeafIndices[index]], emptyList(), commandData)
                }
            } else {
                check(commandsData.size == signers.size) {
                    "Invalid Transaction. Sizes of CommandData (${commandsData.size}) and Signers (${signers.size}) do not match"
                }
                commandsData.lazyMapped { commandData, index ->
                    // Deprecated signingParties property not supported.
                    CommandWithParties(signers[index], emptyList(), commandData)
                }
            }
        }
    }
}
