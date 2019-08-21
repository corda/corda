package net.corda.node.internal

import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.internal.CheckpointSerializationDefaults
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.SubFlow
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.serialization.internal.CheckpointSerializeAsTokenContextImpl
import net.corda.serialization.internal.withTokenContext

object CheckpointVerifier {

    /**
     * Verifies that all Checkpoints stored in the db can be safely loaded with the currently installed version.
     * @throws CheckpointIncompatibleException if any offending checkpoint is found.
     */
    fun verifyCheckpointsCompatible(
            checkpointStorage: CheckpointStorage,
            currentCordapps: List<Cordapp>,
            platformVersion: Int,
            serviceHub: ServiceHub,
            tokenizableServices: List<Any>
    ) {
        val checkpointSerializationContext = CheckpointSerializationDefaults.CHECKPOINT_CONTEXT.withTokenContext(
                CheckpointSerializeAsTokenContextImpl(
                        tokenizableServices,
                        CheckpointSerializationDefaults.CHECKPOINT_SERIALIZER,
                        CheckpointSerializationDefaults.CHECKPOINT_CONTEXT,
                        serviceHub
                )
        )

        val cordappsByHash = currentCordapps.associateBy { it.jarHash }

        checkpointStorage.getAllCheckpoints().use {
            it.forEach { (_, serializedCheckpoint) ->
                val checkpoint = try {
                    serializedCheckpoint.checkpointDeserialize(context = checkpointSerializationContext)
                } catch (e: ClassNotFoundException) {
                    val message = e.message
                    if (message != null) {
                        throw CheckpointIncompatibleException.CordappNotInstalledException(message)
                    } else {
                        throw CheckpointIncompatibleException.CannotBeDeserialisedException(e)
                    }
                } catch (e: Exception) {
                    throw CheckpointIncompatibleException.CannotBeDeserialisedException(e)
                }

                // For each Subflow, compare the checkpointed version to the current version.
                checkpoint.subFlowStack.forEach { checkFlowCompatible(it, cordappsByHash, platformVersion) }
            }
        }
    }

    // Throws exception when the flow is incompatible
    private fun checkFlowCompatible(subFlow: SubFlow, currentCordappsByHash: Map<SecureHash.SHA256, Cordapp>, platformVersion: Int) {
        val subFlowVersion = subFlow.subFlowVersion

        if (subFlowVersion.platformVersion != platformVersion) {
            throw CheckpointIncompatibleException.SubFlowCoreVersionIncompatibleException(subFlow.flowClass, subFlowVersion.platformVersion)
        }

        // If the sub-flow is from a CorDapp then make sure we have that exact CorDapp jar loaded
        if (subFlowVersion is SubFlowVersion.CorDappFlow && subFlowVersion.corDappHash !in currentCordappsByHash) {
            // If we don't then see if the flow exists in any of the CorDapps so that we can give the user a more useful error message
            val matchingCordapp = currentCordappsByHash.values.find { subFlow.flowClass in it.allFlows }
            if (matchingCordapp != null) {
                throw CheckpointIncompatibleException.FlowVersionIncompatibleException(subFlow.flowClass, matchingCordapp, subFlowVersion.corDappHash)
            } else {
                throw CheckpointIncompatibleException.CordappNotInstalledException(subFlow.flowClass.name)
            }
        }
    }
}

/**
 * Thrown at startup, if a checkpoint is found that is incompatible with the current code
 */
sealed class CheckpointIncompatibleException(override val message: String) : Exception() {
    class CannotBeDeserialisedException(val e: Exception) : CheckpointIncompatibleException(
            "Found checkpoint that cannot be deserialised using the current Corda version. Please revert to the previous version of Corda, " +
                    "drain your node (see https://docs.corda.net/upgrading-cordapps.html#flow-drains), and try again. Cause: ${e.message}")

    class SubFlowCoreVersionIncompatibleException(val flowClass: Class<out FlowLogic<*>>, oldVersion: Int) : CheckpointIncompatibleException(
            "Found checkpoint for flow: $flowClass that is incompatible with the current Corda platform. Please revert to the previous " +
                    "version of Corda (version $oldVersion), drain your node (see https://docs.corda.net/upgrading-cordapps.html#flow-drains), and try again.")

    class FlowVersionIncompatibleException(val flowClass: Class<out FlowLogic<*>>, val cordapp: Cordapp, oldHash: SecureHash) : CheckpointIncompatibleException(
            "Found checkpoint for flow: $flowClass that is incompatible with the current installed version of ${cordapp.name}. " +
                    "Please reinstall the previous version of the CorDapp (with hash: $oldHash), drain your node " +
                    "(see https://docs.corda.net/upgrading-cordapps.html#flow-drains), and try again.")

    class CordappNotInstalledException(classNotFound: String) : CheckpointIncompatibleException(
            "Found checkpoint for CorDapp that is no longer installed. Specifically, could not find class $classNotFound. Please install the " +
                    "missing CorDapp, drain your node (see https://docs.corda.net/upgrading-cordapps.html#flow-drains), and try again.")
}

