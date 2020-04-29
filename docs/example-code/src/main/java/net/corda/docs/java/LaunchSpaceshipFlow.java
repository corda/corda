package net.corda.docs.java;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;

@SuppressWarnings("ALL")
// DOCSTART LaunchSpaceshipFlow
@InitiatingFlow
class LaunchSpaceshipFlow extends FlowLogic<Void> {
    @Suspendable
    @Override
    public Void call() throws FlowException {
        boolean shouldLaunchSpaceship = receive(Boolean.class, getPresident()).unwrap(s -> s);
        if (shouldLaunchSpaceship) {
            launchSpaceship();
        }
        return null;
    }

    public void launchSpaceship() {
    }

    public Party getPresident() {
        throw new AbstractMethodError();
    }
}

@InitiatedBy(LaunchSpaceshipFlow.class)
@InitiatingFlow
class PresidentSpaceshipFlow extends FlowLogic<Void> {
    private final Party launcher;

    public PresidentSpaceshipFlow(Party launcher) {
        this.launcher = launcher;
    }

    @Suspendable
    @Override
    public Void call() {
        boolean needCoffee = true;
        send(getSecretary(), needCoffee);
        boolean shouldLaunchSpaceship = false;
        send(launcher, shouldLaunchSpaceship);
        return null;
    }

    public Party getSecretary() {
        throw new AbstractMethodError();
    }
}

@InitiatedBy(PresidentSpaceshipFlow.class)
class SecretaryFlow extends FlowLogic<Void> {
    private final Party president;

    public SecretaryFlow(Party president) {
        this.president = president;
    }

    @Suspendable
    @Override
    public Void call() {
        // ignore
        return null;
    }
}
// DOCEND LaunchSpaceshipFlow

@SuppressWarnings("ALL")
// DOCSTART LaunchSpaceshipFlowCorrect
@InitiatingFlow
class LaunchSpaceshipFlowCorrect extends FlowLogic<Void> {
    @Suspendable
    @Override
    public Void call() throws FlowException {
        FlowSession presidentSession = initiateFlow(getPresident());
        boolean shouldLaunchSpaceship = presidentSession.receive(Boolean.class).unwrap(s -> s);
        if (shouldLaunchSpaceship) {
            launchSpaceship();
        }
        return null;
    }

    public void launchSpaceship() {
    }

    public Party getPresident() {
        throw new AbstractMethodError();
    }
}

@InitiatedBy(LaunchSpaceshipFlowCorrect.class)
@InitiatingFlow
class PresidentSpaceshipFlowCorrect extends FlowLogic<Void> {
    private final FlowSession launcherSession;

    public PresidentSpaceshipFlowCorrect(FlowSession launcherSession) {
        this.launcherSession = launcherSession;
    }

    @Suspendable
    @Override
    public Void call() {
        boolean needCoffee = true;
        FlowSession secretarySession = initiateFlow(getSecretary());
        secretarySession.send(needCoffee);
        boolean shouldLaunchSpaceship = false;
        launcherSession.send(shouldLaunchSpaceship);
        return null;
    }

    public Party getSecretary() {
        throw new AbstractMethodError();
    }
}

@InitiatedBy(PresidentSpaceshipFlowCorrect.class)
class SecretaryFlowCorrect extends FlowLogic<Void> {
    private final FlowSession presidentSession;

    public SecretaryFlowCorrect(FlowSession presidentSession) {
        this.presidentSession = presidentSession;
    }

    @Suspendable
    @Override
    public Void call() {
        // ignore
        return null;
    }
}
// DOCEND LaunchSpaceshipFlowCorrect
