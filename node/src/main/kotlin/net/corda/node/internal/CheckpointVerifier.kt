package net.corda.node.internal

import net.corda.core.cordapp.Cordapp
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.SubFlow
import net.corda.node.services.statemachine.SubFlowVersion

object CheckpointVerifier {

    /**
     * Verifies that all Checkpoints stored in the db can be safely loaded with the currently installed version.
     * @throws CheckpointIncompatibleException if any offending checkpoint is found.
     */
    fun verifyCheckpointsCompatible(checkpointStorage: CheckpointStorage, currentCordapps: List<Cordapp>, platformVersion: Int) {
        checkpointStorage.getAllCheckpoints().forEach { (_, serializedCheckpoint) ->
            val checkpoint = try {
                serializedCheckpoint.deserialize(context = SerializationDefaults.CHECKPOINT_CONTEXT)
            } catch (e: Exception) {
                throw CheckpointIncompatibleException.CannotBeDeserialisedException(e)
            }

            // For each Subflow, compare the checkpointed version to the current version.
            checkpoint.subFlowStack.forEach { checkFlowCompatible(it, currentCordapps, platformVersion) }
        }
    }

    // Throws exception when the flow is incompatible
    private fun checkFlowCompatible(subFlow: SubFlow, currentCordapps: List<Cordapp>, platformVersion: Int) {
        val corDappInfo = subFlow.subFlowVersion

        if (corDappInfo.platformVersion != platformVersion) {
            throw CheckpointIncompatibleException.SubFlowCoreVersionIncompatibleException(subFlow.flowClass, corDappInfo.platformVersion)
        }

        if (corDappInfo is SubFlowVersion.CorDappFlow) {
            val installedCordapps = currentCordapps.filter { it.name == corDappInfo.corDappName }
            when (installedCordapps.size) {
                0 -> throw CheckpointIncompatibleException.FlowNotInstalledException(subFlow.flowClass)
                1 -> {
                    val currenCordapp = installedCordapps.first()
                    if (corDappInfo.corDappHash != currenCordapp.jarHash) {
                        throw CheckpointIncompatibleException.FlowVersionIncompatibleException(subFlow.flowClass, currenCordapp, corDappInfo.corDappHash)
                    }
                }
                2 -> throw IllegalStateException("Multiple Cordapps with name ${corDappInfo.corDappName} installed.") // This should not happen
            }
        }
    }
}

/**
 * Thrown at startup, if a checkpoint is found that is incompatible with the current code
 */
sealed class CheckpointIncompatibleException(override val message: String) : Exception() {
    class CannotBeDeserialisedException(val e: Exception) : CheckpointIncompatibleException(
            "Found checkpoint that cannot be deserialised using the current Corda version. Please revert to the previous version of Corda, drain your node, and try again. Cause: ${e.message}")

    class SubFlowCoreVersionIncompatibleException(val flowClass: Class<out FlowLogic<*>>, oldVersion: Int) : CheckpointIncompatibleException(
            "Found checkpoint for flow: ${flowClass} that is incompatible with the current Corda platform. Please revert to the previous version of Corda (v = ${oldVersion}), drain your node, and try again.")

    class FlowVersionIncompatibleException(val flowClass: Class<out FlowLogic<*>>, val cordapp: Cordapp, oldHash: SecureHash) : CheckpointIncompatibleException(
            "Found checkpoint for flow: ${flowClass} that is incompatible with the current installed version of ${cordapp.name}. Please reinstall the previous version of the CorDapp (with hash: ${oldHash}), drain your node, and try again.")

    class FlowNotInstalledException(val flowClass: Class<out FlowLogic<*>>) : CheckpointIncompatibleException(
            "Found checkpoint for flow: ${flowClass} that is no longer installed. Please install the missing CorDapp, drain your node, and try again.")
}

