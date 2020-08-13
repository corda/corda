package net.corda.nodeapi.internal

import net.corda.core.internal.VisibleForTesting

class JVMAgentUtilities {

    companion object {
        @VisibleForTesting
        @Suppress("NestedBlockDepth")
        fun parseDebugPort(args: Iterable<String>): Short? {
            val debugArgumentPrefix = "-agentlib:jdwp="
            for (arg in args) {
                if (arg.startsWith(debugArgumentPrefix)) {
                    for (keyValuePair in arg.substring(debugArgumentPrefix.length + 1).split(",")) {
                        val equal = keyValuePair.indexOf('=')
                        if (equal >= 0 && keyValuePair.startsWith("address")) {
                            val portBegin = (keyValuePair.lastIndexOf(':').takeUnless { it < 0 } ?: equal) + 1
                            return keyValuePair.substring(portBegin).toShort()
                        }
                    }
                }
            }
            return null
        }
    }
}