package net.corda.core.node

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CordaVersionInfo(val version: String,
                            val revision: String,
                            val platformVersion: Int,
                            val vendor: String)