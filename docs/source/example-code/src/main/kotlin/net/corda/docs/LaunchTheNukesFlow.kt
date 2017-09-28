package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// DOCSTART LaunchTheNukesFlow
@InitiatingFlow
class LaunchTheNukesFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val shouldLaunchNukes = receive<Boolean>(getPresident()).unwrap { it }
        if (shouldLaunchNukes) {
            launchTheNukes()
        }
    }

    fun launchTheNukes() {
    }

    fun getPresident(): Party {
        TODO()
    }
}

@InitiatedBy(LaunchTheNukesFlow::class)
@InitiatingFlow
class PresidentNukeFlow(val launcher: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val needCoffee = true
        send(getSecretary(), needCoffee)
        val shouldLaunchNukes = false
        send(launcher, shouldLaunchNukes)
    }

    fun getSecretary(): Party {
        TODO()
    }
}

@InitiatedBy(PresidentNukeFlow::class)
class SecretaryFlow(val president: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // ignore
    }
}
// DOCEND LaunchTheNukesFlow

// DOCSTART LaunchTheNukesFlowCorrect
@InitiatingFlow
class LaunchTheNukesFlowCorrect : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val presidentSession = initiateFlow(getPresident())
        val shouldLaunchNukes = presidentSession.receive<Boolean>().unwrap { it }
        if (shouldLaunchNukes) {
            launchTheNukes()
        }
    }

    fun launchTheNukes() {
    }

    fun getPresident(): Party {
        TODO()
    }
}

@InitiatedBy(LaunchTheNukesFlowCorrect::class)
@InitiatingFlow
class PresidentNukeFlowCorrect(val launcherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val needCoffee = true
        val secretarySession = initiateFlow(getSecretary())
        secretarySession.send(needCoffee)
        val shouldLaunchNukes = false
        launcherSession.send(shouldLaunchNukes)
    }

    fun getSecretary(): Party {
        TODO()
    }
}

@InitiatedBy(PresidentNukeFlowCorrect::class)
class SecretaryFlowCorrect(val presidentSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // ignore
    }
}
// DOCEND LaunchTheNukesFlowCorrect
