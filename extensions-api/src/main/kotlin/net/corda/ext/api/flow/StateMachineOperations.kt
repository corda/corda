package net.corda.ext.api.flow

import net.corda.core.CordaInternal

@CordaInternal
interface StateMachineOperations : AsyncFlowStarter, ExistingFlowsOperations {

    val transactionMappingFeed: StateMachineTransactionMappingFeed

    val flowHospital: FlowHospital

    val checkpointDumper: CheckpointDumper
}