package net.corda.node.services

import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.SingleThreadedStateMachineManager
import net.corda.node.services.statemachine.StaffedFlowHospital
import org.junit.After

abstract class StateMachineCleanUp {

    /**
     * State Machine statics clean up. Newly introduced statics in state machine classes should be added below.
     */
    @After
    fun hooksCleanUp() {
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = null

        SingleThreadedStateMachineManager.beforeClientIDCheck = null
        SingleThreadedStateMachineManager.onClientIDNotFound = null
        SingleThreadedStateMachineManager.onCallingStartFlowInternal = null
        SingleThreadedStateMachineManager.onStartFlowInternalThrewAndAboutToRemove = null

        StaffedFlowHospital.onFlowKeptForOvernightObservation.clear()
        StaffedFlowHospital.onFlowDischarged.clear()
        StaffedFlowHospital.onFlowErrorPropagated.clear()
        StaffedFlowHospital.onFlowResuscitated.clear()
        StaffedFlowHospital.onFlowAdmitted.clear()
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.clear()
    }
}