package net.corda.healthsurvey.collectors

import net.corda.healthsurvey.output.Report
import java.io.File
import java.time.Instant

class MachineInformationCollector : TrackedCollector("Collecting machine information ...") {

    override fun collect(report: Report) {
        val maxMemory = Runtime.getRuntime().maxMemory().let {
            if (it == java.lang.Long.MAX_VALUE) { "no limit" } else { "$it bytes" }
        }
        val file = report.addFile("machine-information.txt")
                .withContent("Date and Time:                    ${Instant.now()}\n")
                .withContent("Operating System Name:            ${System.getProperty("os.name")}\n")
                .withContent("Operating System Version:         ${System.getProperty("os.version")}\n")
                .withContent("Architecture:                     ${System.getProperty("os.arch")}\n")
                .withContent("Java Home:                        ${System.getProperty("java.home")}\n")
                .withContent("Java Vendor:                      ${System.getProperty("java.vendor")}\n")
                .withContent("Java Version:                     ${System.getProperty("java.version")}\n")
                .withContent("Java Class Version:               ${System.getProperty("java.class.version")}\n")
                .withContent("Java Class Path:                  ${System.getProperty("java.class.path")}\n")
                .withContent("Java Library Path:                ${System.getProperty("java.library.path")}\n")
                .withContent("Java Temporary Directory:         ${System.getProperty("java.io.tmpdir")}\n")
                .withContent("Available Processor Cores:        ${Runtime.getRuntime().availableProcessors()}\n")
                .withContent("Free Memory:                      ${Runtime.getRuntime().freeMemory()} bytes\n")
                .withContent("Maximum Memory:                   $maxMemory\n")
                .withContent("Total Memory Available in JVM:    ${Runtime.getRuntime().totalMemory()} bytes\n")

        for (root in File.listRoots()) {
            file.withContent("File System Root:                 ${root.absolutePath}\n")
            file.withContent(" - Total Space:                   ${root.totalSpace} bytes\n")
            file.withContent(" - Free Space:                    ${root.freeSpace} bytes\n")
            file.withContent(" - Usable Space:                  ${root.usableSpace} bytes\n")
        }
        complete("Collected machine information")
    }

}
