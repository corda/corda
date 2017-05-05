package net.corda.node.driver

import com.google.common.net.HostAndPort
import net.corda.core.utilities.DUMMY_MAP
import org.bouncycastle.asn1.x500.X500Name

abstract class NetworkMapStrategy(internal val startDedicated: Boolean, internal val legalName: X500Name) {
    internal abstract fun serviceConfig(dedicatedAddress: HostAndPort, nodeName: X500Name, p2pAddress: HostAndPort): Map<String, String>?
}

private fun toServiceConfig(address: HostAndPort, legalName: X500Name) = mapOf(
        "address" to address.toString(),
        "legalName" to legalName.toString()
)

abstract class AbstractDedicatedNetworkMap(start: Boolean) : NetworkMapStrategy(start, DUMMY_MAP.name) {
    override fun serviceConfig(dedicatedAddress: HostAndPort, nodeName: X500Name, p2pAddress: HostAndPort) = toServiceConfig(dedicatedAddress, legalName)
}

object FalseNetworkMap : AbstractDedicatedNetworkMap(false)

object DedicatedNetworkMap : AbstractDedicatedNetworkMap(true)

class NominatedNetworkMap(legalName: X500Name, private val address: HostAndPort) : NetworkMapStrategy(false, legalName) {
    override fun serviceConfig(dedicatedAddress: HostAndPort, nodeName: X500Name, p2pAddress: HostAndPort) = if (nodeName != legalName) {
        toServiceConfig(address, legalName)
    } else {
        p2pAddress == address || throw IllegalArgumentException("Passed-in address $address of nominated network map $legalName is wrong, it should be: $p2pAddress")
        null
    }
}
