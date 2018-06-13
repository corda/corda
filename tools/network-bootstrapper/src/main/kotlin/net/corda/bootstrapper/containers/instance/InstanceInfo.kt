package net.corda.bootstrapper.containers.instance

data class InstanceInfo(val groupId: String,
                        val instanceName: String,
                        val instanceAddress: String,
                        val reachableAddress: String,
                        val portMapping: Map<Int, Int> = emptyMap())