package net.corda.bn.testing

import net.corda.bn.states.BNIdentity
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class DummyIdentity(val name: String) : BNIdentity