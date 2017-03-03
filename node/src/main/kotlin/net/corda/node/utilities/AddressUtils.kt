package net.corda.node.utilities

import java.net.InetAddress
import java.net.NetworkInterface

object AddressUtils {
    /** Returns the first public IP address found on any of the network interfaces, or `null` if none found. */
    fun tryDetectPublicIP(): InetAddress? {
        for (int in NetworkInterface.getNetworkInterfaces()) {
            for (address in int.inetAddresses) {
                if (isPublic(address)) return address
            }
        }
        return null
    }

    /** Returns `true` if the provided `address` is a public IP address or hostname. */
    fun isPublic(address: InetAddress): Boolean {
        return !(address.isSiteLocalAddress || address.isAnyLocalAddress ||
                address.isLinkLocalAddress || address.isLoopbackAddress ||
                address.isMulticastAddress)
    }

    fun isPublic(hostText: String) = isPublic(InetAddress.getByName(hostText))
}