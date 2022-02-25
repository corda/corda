package com.r3.conclave.cordapp.common.dto

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.WireTransaction

@CordaSerializable
data class WireTxAdditionalInfo(
        val wireTransaction: WireTransaction,
        val inputStates: Array<StateAndRef<ContractState>>,
        val attachments: Array<Attachment>,
        val conclaveNetworkParameters: ConclaveNetworkParameters,
        val references: Array<StateAndRef<ContractState>>,
        val serializedInputs: Array<ByteArray>?,
        val serializedReferences: Array<ByteArray>?
)