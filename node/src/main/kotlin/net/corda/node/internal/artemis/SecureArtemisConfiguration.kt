package net.corda.node.internal.artemis

import net.corda.core.crypto.newSecureRandom
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import java.math.BigInteger

internal open class SecureArtemisConfiguration : ConfigurationImpl() {
    init {
        // Artemis allows multiple servers to be grouped together into a cluster for load balancing purposes. The cluster
        // user is used for connecting the nodes together. It has super-user privileges and so it's imperative that its
        // password be changed from the default (as warned in the docs). Since we don't need this feature we turn it off
        // by having its password be an unknown securely random 128-bit value.
        clusterPassword = BigInteger(128, newSecureRandom()).toString(16)
    }
}