package net.corda.observerdemo.contracts

import net.corda.core.identity.AbstractParty

interface ObservedState {
    val observers: List<AbstractParty>
}