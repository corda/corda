package com.r3.conclave.cordapp.sample.enclave

import com.r3.conclave.cordapp.common.dto.ConclaveNetworkParameters
import com.r3.conclave.cordapp.common.dto.WireTxAdditionalInfo
import net.corda.core.contracts.CommandWithParties
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.LedgerTransaction

class LedgerTxHelper {

    companion object {

        @JvmStatic
        fun toLedgerTxInternal(conclaveLedgerTxModel: WireTxAdditionalInfo): LedgerTransaction {

            val serializedResolvedInputs = conclaveLedgerTxModel.serializedInputs?.mapIndexed { index, ref ->
                SerializedStateAndRef(SerializedBytes(ref),  conclaveLedgerTxModel
                    .inputStates[index].ref)
            }

            val serializedResolvedReferences =  conclaveLedgerTxModel.serializedReferences?.mapIndexed { index, ref ->
                SerializedStateAndRef(SerializedBytes(ref),  conclaveLedgerTxModel.references[index].ref)
            }

            return LedgerTransaction.createForConclaveVerify(
                conclaveLedgerTxModel.inputStates.toList(),
                conclaveLedgerTxModel.wireTransaction.outputs,
                conclaveLedgerTxModel.wireTransaction.commands.map {
                    CommandWithParties(it.signers, emptyList(), it.value)
                },
                conclaveLedgerTxModel.attachments.toList(),
                conclaveLedgerTxModel.wireTransaction.id,
                conclaveLedgerTxModel.wireTransaction.notary,
                conclaveLedgerTxModel.wireTransaction.timeWindow,
                conclaveLedgerTxModel.wireTransaction.privacySalt,
                toNetworkParameters(conclaveLedgerTxModel.conclaveNetworkParameters),
                conclaveLedgerTxModel.references.toList(),
                conclaveLedgerTxModel.wireTransaction.componentGroups,
                serializedResolvedInputs,
                serializedResolvedReferences,
                conclaveLedgerTxModel.wireTransaction.digestService
            )
        }

        @JvmStatic
        fun toNetworkParameters(conclaveNetworkParameters: ConclaveNetworkParameters): NetworkParameters {

            return NetworkParameters(
                conclaveNetworkParameters.minimumPlatformVersion,
                conclaveNetworkParameters.notaries.toList(),
                conclaveNetworkParameters.maxMessageSize,
                conclaveNetworkParameters.maxTransactionSize,
                conclaveNetworkParameters.modifiedTime,
                conclaveNetworkParameters.epoch,
                conclaveNetworkParameters.whitelistedContractImplementations.map {
                    it.first to it.second.asList()
                }.toMap(),
                conclaveNetworkParameters.eventHorizon,
                conclaveNetworkParameters.packageOwnership.toMap()
            )
        }
    }


}