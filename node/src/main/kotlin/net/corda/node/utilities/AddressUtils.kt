/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import java.net.InetAddress
import java.net.NetworkInterface

object AddressUtils {
    private const val REACHABLE_TIMEOUT_MS = 1000

    /** Returns the first public IP address found on any of the network interfaces, or `null` if none found. */
    fun tryDetectPublicIP(): InetAddress? {
        for (int in NetworkInterface.getNetworkInterfaces()) {
            if (int.isLoopback) continue

            for (address in int.inetAddresses) {
                if (isPublic(address) && address.isReachable(REACHABLE_TIMEOUT_MS))
                    return address
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