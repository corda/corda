package net.corda

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort

@CordaSerializable
data class RpcInfo(val address: NetworkHostAndPort, val username: String, val password: String)