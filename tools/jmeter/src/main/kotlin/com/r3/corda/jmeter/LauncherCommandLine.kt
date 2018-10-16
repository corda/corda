package com.r3.corda.jmeter

import picocli.CommandLine

@CommandLine.Command(synopsisHeading = "", customSynopsis = arrayOf("Usage: jmeter-corda [OPTIONS] -- [<jMeter args>...]"), sortOptions = false)
class LauncherCommandLine {
    @CommandLine.Option(names = arrayOf("-?", "-h", "--help"), usageHelp = true, description = arrayOf("Prints usage"))
    var helpRequested: Boolean = false

    @CommandLine.Option(names = arrayOf("-Xssh"), description = arrayOf("List of hosts to create SSH tunnels to, separated by space",
            "Example: -Xssh <hostname1> [<hostname2> ...]"), arity = "1..*")
    var sshHosts: MutableList<String> = mutableListOf()

    @CommandLine.Option(names = arrayOf("-XsshUser"), description = arrayOf(
            "Remote user account to use for ssh tunnels. This defaults to the current user."))
    var sshUser: String = System.getProperty("user.name")

    @CommandLine.Option(
            names = arrayOf("-XadditionalSearchPaths"),
            description = arrayOf(
                    "A semicolon separated list of directories/jar files to search for plug-ins/samplers. This will be added to any search paths already in the properties",
                    "Example:",
                    "-XadditionalSearchPaths [<class dir>|<jar file>][;...]"))
    var additionalSearchPaths = ""

    @CommandLine.Option(names = arrayOf("-XjmeterProperties"), description = arrayOf(
            "Path to a jmeter.properties file to replace the one in the jar. Use this instead of the -p flag of jMeter as the wrapping code needs access to the file as well."))
    var jMeterProperties = ""

    @CommandLine.Option(names = arrayOf("-XserverRmiMappings"), description = arrayOf(
            "Path to a server RMI port mappings file.",
            "This file should have a line for each server that needs an ssh tunnel created to. Each line should have the unqualified server name, followed by a colon and the port number. Each host needs a different port number. Lines starting with # are comments and are ignored.",
            "Example line for host example-server.corda.net:",
            "example-server:10101"))
    var serverRmiMappings: String = ""

    @CommandLine.Parameters(paramLabel = "<jMeter args>", description = arrayOf("All arguments after -- are passed to JMeter"))
    var jMeterArguments: MutableList<String> = mutableListOf()
}