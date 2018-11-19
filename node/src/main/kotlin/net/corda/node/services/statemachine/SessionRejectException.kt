package net.corda.node.services.statemachine

import net.corda.core.CordaException
import net.corda.core.flows.FlowLogic

/**
 * An exception propagated and thrown in case a session initiation fails.
 */
open class SessionRejectException(message: String) : CordaException(message) {
    class UnknownClass(val initiatorFlowClassName: String) : SessionRejectException("Don't know $initiatorFlowClassName")

    class NotAFlow(val initiatorClass: Class<*>) : SessionRejectException("${initiatorClass.name} is not a flow")

    class NotRegistered(val initiatorFlowClass: Class<out FlowLogic<*>>) : SessionRejectException("${initiatorFlowClass.name} is not registered")

    class FinalityHandlerDisabled : SessionRejectException("Counterparty attempting to use the old insecure API of FinalityFlow. However this " +
            "API is disabled on this node since there no CorDapps installed that require it. It may be that the counterparty is running an " +
            "older verison of a CorDapp.")
}
