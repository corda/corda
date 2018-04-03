package net.corda

import net.corda.annotations.serialization.Serializable
import net.corda.core.utilities.NetworkHostAndPort

@Serializable
data class RpcInfo(val address: NetworkHostAndPort, val username: String, val password: String)