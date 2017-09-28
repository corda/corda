package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// DOCSTART LaunchSpaceshipFlow
@InitiatingFlow
class LaunchSpaceshipFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val shouldLaunchSpaceship = receive<Boolean>(getPresident()).unwrap { it }
        if (shouldLaunchSpaceship) {
            launchSpaceship()
        }
    }

    fun launchSpaceship() {
    }

    fun getPresident(): Party {
        TODO()
    }
}

@InitiatedBy(LaunchSpaceshipFlow::class)
@InitiatingFlow
class PresidentSpaceshipFlow(val launcher: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val needCoffee = true
        send(getSecretary(), needCoffee)
        val shouldLaunchSpaceship = false
        send(launcher, shouldLaunchSpaceship)
    }

    fun getSecretary(): Party {
        TODO()
    }
}

@InitiatedBy(PresidentSpaceshipFlow::class)
class SecretaryFlow(val president: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // ignore
    }
}
// DOCEND LaunchSpaceshipFlow

// DOCSTART LaunchSpaceshipFlowCorrect
@InitiatingFlow
class LaunchSpaceshipFlowCorrect : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val presidentSession = initiateFlow(getPresident())
        val shouldLaunchSpaceship = presidentSession.receive<Boolean>().unwrap { it }
        if (shouldLaunchSpaceship) {
            launchSpaceship()
        }
    }

    fun launchSpaceship() {
    }

    fun getPresident(): Party {
        TODO()
    }
}

@InitiatedBy(LaunchSpaceshipFlowCorrect::class)
@InitiatingFlow
class PresidentSpaceshipFlowCorrect(val launcherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val needCoffee = true
        val secretarySession = initiateFlow(getSecretary())
        secretarySession.send(needCoffee)
        val shouldLaunchSpaceship = false
        launcherSession.send(shouldLaunchSpaceship)
    }

    fun getSecretary(): Party {
        TODO()
    }
}

@InitiatedBy(PresidentSpaceshipFlowCorrect::class)
class SecretaryFlowCorrect(val presidentSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // ignore
    }
}
// DOCEND LaunchSpaceshipFlowCorrect
