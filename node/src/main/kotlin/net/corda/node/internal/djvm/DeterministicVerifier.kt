package net.corda.node.internal.djvm

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ComponentGroupEnum.*
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.execution.*
import net.corda.djvm.messages.Message
import net.corda.djvm.source.ClassSource
import net.corda.node.djvm.LtxFactory

class DeterministicVerifier(
    ltx: LedgerTransaction,
    transactionClassLoader: ClassLoader,
    private val sandboxConfiguration: SandboxConfiguration
) : Verifier(ltx, transactionClassLoader) {

    override fun verifyContracts() {
        val result = IsolatedTask(ltx.id.toString(), sandboxConfiguration).run {
            val taskFactory = classLoader.createRawTaskFactory()
            val sandboxBasicInput = classLoader.createBasicInput()

            /**
             * Deserialise the [LedgerTransaction] again into something
             * that we can execute inside the DJVM's sandbox.
             */
            val sandboxTx = ltx.transform { componentGroups, serializedInputs, serializedReferences ->
                val serializer = Serializer(classLoader)
                val componentFactory = ComponentFactory(
                    classLoader,
                    taskFactory,
                    sandboxBasicInput,
                    serializer,
                    componentGroups
                )
                val attachmentFactory = AttachmentFactory(
                    classLoader,
                    taskFactory,
                    sandboxBasicInput,
                    serializer
                )

                val idData = ltx.id.serialize()
                val notaryData = ltx.notary?.serialize()
                val timeWindowData = ltx.timeWindow?.serialize()
                val privacySaltData = ltx.privacySalt.serialize()
                val networkingParametersData = ltx.networkParameters?.serialize()

                val createSandboxTx = classLoader.createTaskFor(taskFactory, LtxFactory::class.java)
                createSandboxTx.apply(arrayOf(
                    serializer.deserialize(serializedInputs),
                    componentFactory.toSandbox(OUTPUTS_GROUP, TransactionState::class.java),
                    CommandFactory(classLoader, taskFactory).toSandbox(
                        componentFactory.toSandbox(SIGNERS_GROUP, List::class.java),
                        componentFactory.toSandbox(COMMANDS_GROUP, CommandData::class.java),
                        componentFactory.calculateLeafIndicesFor(COMMANDS_GROUP)
                    ),
                    attachmentFactory.toSandbox(ltx.attachments),
                    serializer.deserialize(idData),
                    serializer.deserialize(notaryData),
                    serializer.deserialize(timeWindowData),
                    serializer.deserialize(privacySaltData),
                    serializer.deserialize(networkingParametersData),
                    serializer.deserialize(serializedReferences)
                ))
            }

            val verifier = classLoader.createTaskFor(taskFactory, ContractVerifier::class.java)

            // Now execute the contract verifier task within the sandbox...
            verifier.apply(sandboxTx)
        }

        with (result.costs) {
            logger.info("Verify {} complete: allocations={}, invocations={}, jumps={}, throws={}",
                        ltx.id, allocations, invocations, jumps, throws)
        }

        result.exception?.run {
            val sandboxEx = SandboxException(
                Message.getMessageFromException(this),
                result.identifier,
                ClassSource.fromClassName(ContractVerifier::class.java.name),
                ExecutionSummary(result.costs),
                this
            )
            logger.error("Error validating transaction ${ltx.id}.", sandboxEx)
            throw DeterministicVerificationException(ltx.id, sandboxEx.message ?: "", sandboxEx)
        }
    }
}

class DeterministicVerificationException(txId: SecureHash, message: String, cause: Throwable)
    : TransactionVerificationException(txId, message, cause)
