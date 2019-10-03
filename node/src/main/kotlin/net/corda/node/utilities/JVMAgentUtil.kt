package net.corda.node.utilities

import com.sun.tools.attach.VirtualMachine
import org.slf4j.Logger
import java.lang.management.ManagementFactory
import java.util.*

object JVMAgentUtil {
    /**
     * Utility to attach to own VM at run-time and obtain agent details.
     * In Java 9 this requires setting the following run-time jvm flag: -Djdk.attach.allowAttachSelf=true
     * This mechanism supersedes the usage of VMSupport which is not available from Java 9 onwards.
     */
    fun getJvmAgentProperties(log: Logger): Properties {
        val jvmPid = ManagementFactory.getRuntimeMXBean().name.substringBefore('@')
        return try {
            val vm = VirtualMachine.attach(jvmPid)
            return vm.agentProperties
        } catch (e: Throwable) {
            log.warn("Unable to determine whether agent is running: ${e.message}.\n" +
                     "You may need to pass in -Djdk.attach.allowAttachSelf=true if running on a Java 9 or later VM")
            Properties()
        }
    }
}