/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node.configuration

import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class NetworkInterface(
        val host: String = "localhost",
        val sshPort: Int = getPort(2222 + nodeIndex),
        val p2pPort: Int = getPort(12001 + (nodeIndex * 5)),
        val rpcPort: Int = getPort(12002 + (nodeIndex * 5)),
        val rpcProxy: Int = getPort(13002 + (nodeIndex * 5)),
        val rpcAdminPort: Int = getPort(12003 + (nodeIndex * 5)),
        val webPort: Int = getPort(12004 + (nodeIndex * 5)),
        val dbPort: Int = getPort(12005 + (nodeIndex * 5)),
        val dockerPort: Int = getPort(5000 + (nodeIndex * 5))
) : ConfigurationTemplate() {

    init {
        nodeIndex += 1
    }

    override val config: (Configuration) -> String
        get() = {
            """
            |sshd={ port=$sshPort }
            |p2pAddress="$host:$p2pPort"
            |rpcSettings = {
            |    useSsl = false
            |    standAloneBroker = false
            |    address = "$host:$rpcPort"
            |    adminAddress = "$host:$rpcAdminPort"
            |}
            """
        }

    companion object {

        private var nodeIndex = 0

        private var startOfBackupRange = AtomicInteger(40000)

        private fun getPort(suggestedPortNumber: Int): Int {
            var portNumber = suggestedPortNumber
            while (isPortInUse(portNumber)) {
                portNumber = startOfBackupRange.getAndIncrement()
            }
            if (portNumber >= 65535) {
                throw Exception("No free port found (suggested $suggestedPortNumber)")
            }
            return portNumber
        }

        private fun isPortInUse(portNumber: Int): Boolean {
            return try {
                val s = Socket("localhost", portNumber)
                s.close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
