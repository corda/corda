package com.r3.corda.jmeter

import org.junit.Assert.assertTrue
import org.junit.Test
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

class TestCommandLine {
    @Test
    fun checkWeGetJmeterArgs() {
        val args = arrayOf("-Xssh", "server1", "server2", "--", "-p", "/some/properties/file", "-P", "proxy")
        val cli = LauncherCommandLine()
        CommandLine.populateCommand(cli, *args)

        assertEquals(cli.jMeterArguments, listOf("-p", "/some/properties/file", "-P", "proxy"))
        assertEquals(cli.helpRequested, false)
        assertEquals(cli.sshUser, System.getProperty("user.name"))
        assertEquals(cli.sshHosts, listOf("server1", "server2"))

        val stream = ByteArrayOutputStream()
        val ps = PrintStream(stream)
        CommandLine.usage(cli, ps, CommandLine.Help.Ansi.OFF)

        assertEquals("""Usage: jmeter-corda [OPTIONS] -- [<jMeter args>...]
      [<jMeter args>...]    All arguments after -- are passed to JMeter
  -?, -h, --help            Prints usage
      -Xssh=<sshHosts>...   List of hosts to create SSH tunnels to, separated by
                              space
                            Example: -Xssh <hostname1> [<hostname2> ...]
      -XsshUser=<sshUser>   Remote user account to use for ssh tunnels. This
                              defaults to the current user.
      -XadditionalSearchPaths=<additionalSearchPaths>
                            A semicolon separated list of directories/jar files to
                              search for plug-ins/samplers. This will be added to
                              any search paths already in the properties
                            Example:
                            -XadditionalSearchPaths [<class dir>|<jar file>][;...]
      -XjmeterProperties=<jMeterProperties>
                            Path to a jmeter.properties file to replace the one in
                              the jar. Use this instead of the -p flag of jMeter as
                              the wrapping code needs access to the file as well.
      -XserverRmiMappings=<serverRmiMappings>
                            Path to a server RMI port mappings file.
                            This file should have a line for each server that needs
                              an ssh tunnel created to. Each line should have the
                              unqualified server name, followed by a colon and the
                              port number. Each host needs a different port number.
                              Lines starting with # are comments and are ignored.
                            Example line for host example-server.corda.net:
                            example-server:10101
""".replace("\n", System.lineSeparator()), stream.toString())
    }

    @Test
    fun checkDefaultArgs() {
        val cli = LauncherCommandLine()

        assertTrue(cli.jMeterArguments.isEmpty())
        assertEquals(cli.helpRequested, false)
        assertEquals(cli.sshUser, System.getProperty("user.name"))
        assertTrue(cli.sshHosts.isEmpty())
        assertTrue(cli.jMeterProperties.isBlank())
        assertTrue(cli.serverRmiMappings.isBlank())
        assertTrue(cli.additionalSearchPaths.isBlank())
    }
}