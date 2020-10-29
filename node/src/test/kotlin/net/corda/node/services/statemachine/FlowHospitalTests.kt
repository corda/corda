package net.corda.node.services.statemachine

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.flows.StateMachineRunId
import org.junit.After
import org.junit.Test
import java.time.Clock

class FlowHospitalTests {

    private val clock = mock<Clock>()
    private val flowMessaging = mock<FlowMessaging>()
    private val ourSenderUUID = "78d12c2c-12cc-11eb-adc1-0242ac120002"

    private val id = StateMachineRunId.createRandom()
    private val fiber = mock<FlowFiber>()
    private val currentState = mock<StateMachineState>()



    private var flowHospital: StaffedFlowHospital? = null

    @After
    fun cleanUp() {
        flowHospital = null
    }

    @Test(timeout = 300_000)
    fun `Hospital works if not injected with any staff members`() {
        doReturn(id).whenever(fiber).id

        flowHospital = StaffedFlowHospital(flowMessaging, clock, ourSenderUUID)//.also { it.staff = listOf() }

        flowHospital!!.requestTreatment(
            fiber,
            currentState,
            listOf(IllegalStateException())
        )

    }

}