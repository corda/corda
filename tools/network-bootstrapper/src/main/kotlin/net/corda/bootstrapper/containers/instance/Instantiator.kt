package net.corda.bootstrapper.containers.instance

import java.util.concurrent.CompletableFuture


interface Instantiator {
    fun instantiateContainer(imageId: String,
                             portsToOpen: List<Int>,
                             instanceName: String,
                             env: Map<String, String>? = null): CompletableFuture<Pair<String, Map<Int, Int>>>


    companion object {
        val ADDITIONAL_NODE_INFOS_PATH = "/opt/corda/additional-node-infos"
    }

    fun getExpectedFQDN(instanceName: String): String
}