package net.corda.node.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.node.services.config.FlowOverride
import net.corda.node.services.config.FlowOverrideConfig
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import java.lang.IllegalStateException

private val marker = "This is a special marker"

class NodeFlowManagerTest {

    @InitiatingFlow
    class Init : FlowLogic<Unit>() {
        override fun call() {
            TODO("not implemented")
        }
    }

    @InitiatedBy(Init::class)
    open class Resp(val otherSesh: FlowSession) : FlowLogic<Unit>() {
        override fun call() {
            TODO("not implemented")
        }

    }

    @InitiatedBy(Init::class)
    class Resp2(val otherSesh: FlowSession) : FlowLogic<Unit>() {
        override fun call() {
            TODO("not implemented")
        }

    }

    @InitiatedBy(Init::class)
    open class RespSub(sesh: FlowSession) : Resp(sesh) {
        override fun call() {
            TODO("not implemented")
        }

    }

    @InitiatedBy(Init::class)
    class RespSubSub(sesh: FlowSession) : RespSub(sesh) {
        override fun call() {
            TODO("not implemented")
        }

    }


    @Test(expected = IllegalStateException::class)
    fun `should fail to validate if more than one registration with equal weight`() {
        val nodeFlowManager = NodeFlowManager()
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp::class.java)
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp2::class.java)
        nodeFlowManager.validateRegistrations()
    }

    @Test()
    fun `should allow registration of flows with different weights`() {
        val nodeFlowManager = NodeFlowManager()
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp::class.java)
        nodeFlowManager.registerInitiatedFlow(Init::class.java, RespSub::class.java)
        nodeFlowManager.validateRegistrations()
        val factory = nodeFlowManager.getFlowFactoryForInitiatingFlow(Init::class.java)!!
        val flow = factory.createFlow(Mockito.mock(FlowSession::class.java))
        Assert.assertThat(flow, `is`(instanceOf(RespSub::class.java)))
    }

    @Test()
    fun `should allow updating of registered responder at runtime`() {
        val nodeFlowManager = NodeFlowManager()
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp::class.java)
        nodeFlowManager.registerInitiatedFlow(Init::class.java, RespSub::class.java)
        nodeFlowManager.validateRegistrations()
        var factory = nodeFlowManager.getFlowFactoryForInitiatingFlow(Init::class.java)!!
        var flow = factory.createFlow(Mockito.mock(FlowSession::class.java))
        Assert.assertThat(flow, `is`(instanceOf(RespSub::class.java)))
        // update
        nodeFlowManager.registerInitiatedFlow(Init::class.java, RespSubSub::class.java)
        nodeFlowManager.validateRegistrations()

        factory = nodeFlowManager.getFlowFactoryForInitiatingFlow(Init::class.java)!!
        flow = factory.createFlow(Mockito.mock(FlowSession::class.java))
        Assert.assertThat(flow, `is`(instanceOf(RespSubSub::class.java)))
    }

    @Test
    fun `should allow an override to be specified`() {
        val nodeFlowManager = NodeFlowManager(FlowOverrideConfig(listOf(FlowOverride(Init::class.qualifiedName!!, Resp::class.qualifiedName!!))))
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp::class.java)
        nodeFlowManager.registerInitiatedFlow(Init::class.java, Resp2::class.java)
        nodeFlowManager.registerInitiatedFlow(Init::class.java, RespSubSub::class.java)
        nodeFlowManager.validateRegistrations()

        val factory = nodeFlowManager.getFlowFactoryForInitiatingFlow(Init::class.java)!!
        val flow = factory.createFlow(Mockito.mock(FlowSession::class.java))

        Assert.assertThat(flow, `is`(instanceOf(Resp::class.java)))
    }
}