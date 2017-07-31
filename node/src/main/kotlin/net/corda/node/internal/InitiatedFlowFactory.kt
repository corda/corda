package net.corda.node.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.node.services.statemachine.SessionInit
import net.corda.node.services.statemachine.SessionRejectException

interface InitiatedFlowFactory<out F : FlowLogic<*>> {
    fun createFlow(platformVersion: Int, otherParty: Party, sessionInit: SessionInit): F

    data class Core<out F : FlowLogic<*>>(val factory: (Party, Int) -> F) : InitiatedFlowFactory<F> {
        override fun createFlow(platformVersion: Int, otherParty: Party, sessionInit: SessionInit): F {
            return factory(otherParty, platformVersion)
        }
    }

    data class CorDapp<out F : FlowLogic<*>>(val version: Int, val factory: (Party) -> F) : InitiatedFlowFactory<F> {
        override fun createFlow(platformVersion: Int, otherParty: Party, sessionInit: SessionInit): F {
            // TODO Add support for multiple versions of the same flow when CorDapps are loaded in separate class loaders
            if (sessionInit.flowVerison == version) return factory(otherParty)
            throw SessionRejectException(
                    "Version not supported",
                    "Version mismatch - ${sessionInit.initiatingFlowClass} is only registered for version $version")
        }
    }
}

