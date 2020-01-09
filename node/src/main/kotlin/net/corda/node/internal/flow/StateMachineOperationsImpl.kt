package net.corda.node.internal.flow

import net.corda.ext.api.flow.AsyncFlowStarter
import net.corda.ext.api.flow.CheckpointDumper
import net.corda.ext.api.flow.ExistingFlowsOperations
import net.corda.ext.api.flow.FlowHospital
import net.corda.ext.api.flow.StateMachineOperations
import net.corda.ext.api.flow.StateMachineTransactionMappingFeed
import net.corda.node.services.api.FlowStarter

class StateMachineOperationsImpl(efo: ExistingFlowsOperations, flowStarter: FlowStarter, override val flowHospital: FlowHospital,
                                 override val transactionMappingFeed: StateMachineTransactionMappingFeed,
                                 override val checkpointDumper: CheckpointDumper) : StateMachineOperations,
        AsyncFlowStarter by flowStarter, ExistingFlowsOperations by efo