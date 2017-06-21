package net.corda.testing.driver

import com.google.common.net.HostAndPort
import net.corda.core.utilities.DUMMY_MAP
import org.bouncycastle.asn1.x500.X500Name

sealed class NetworkMapStartStrategy {
    internal abstract val startDedicated: Boolean
    internal abstract val legalName: X500Name
    internal fun serviceConfig(address: HostAndPort) = mapOf(
            "address" to address.toString(),
            "legalName" to legalName.toString()
    )

    class Dedicated(startAutomatically: Boolean) : NetworkMapStartStrategy() {
        override val startDedicated = startAutomatically
        override val legalName = DUMMY_MAP.name
    }

    class Nominated(override val legalName: X500Name) : NetworkMapStartStrategy() {
        override val startDedicated = false
    }
}
