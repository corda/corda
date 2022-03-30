package net.corda.core.conclave.common.dto

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
class InputsAndRefsForNode(
        val inputs: Array<StateAndRef<ContractState>>,
        val refs: Array<StateAndRef<ContractState>>
)