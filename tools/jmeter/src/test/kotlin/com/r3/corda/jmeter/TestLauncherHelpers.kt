package com.r3.corda.jmeter

import net.corda.core.internal.div
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestLauncherHelpers {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testJmeterPropPreppingCapsule() {
        val expectedSearchPaths = "${createFileInTmpFolder("corda-1.jar")};${createFileInTmpFolder("jmeter-2.jar")}"
        createFileInTmpFolder("foo.jar")

        SystemPropertySetter("jmeter.home").use {
            val args = arrayOf("-Xssh", "server1", "server2", "--", "-P", "proxy")
            val cli = LauncherCommandLine()
            CommandLine.populateCommand(cli, *args)
            val searchPaths = Launcher.prepareJMeterPropsCapsule(cli, temporaryFolder.root.absolutePath)

            assertEquals(temporaryFolder.root.absolutePath, System.getProperty("jmeter.home"))
            assertEquals(expectedSearchPaths, searchPaths)
            assertTrue((temporaryFolder.root.toPath() / "lib" / "ext").toFile().exists())
            assertTrue((temporaryFolder.root.toPath() / "lib" / "junit").toFile().exists())
        }
    }

    @Test
    fun testCommandLineMungingCapsuleCustomSearchpath() {
        val expectedSearchPaths = "${createFileInTmpFolder("corda-1.jar")};${createFileInTmpFolder("jmeter-2.jar")}"
        createFileInTmpFolder("foo.jar")

        SystemPropertySetter("jmeter.home").use {
            val args = arrayOf("-Xssh", "server1", "server2", "-XadditionalSearchPaths", "bar.jar", "--", "-P", "proxy")
            val cli = LauncherCommandLine()
            CommandLine.populateCommand(cli, *args)
            val searchPaths = Launcher.prepareJMeterPropsCapsule(cli, temporaryFolder.root.absolutePath)
            assertEquals("$expectedSearchPaths;bar.jar", searchPaths)
            assertEquals(temporaryFolder.root.absolutePath, System.getProperty("jmeter.home"))
        }
    }

    @Test
    fun testCommandLineMungingGradleCustomSearchPath() {
        val defaultSearchPaths = "/foo/bar/baz.jar;/some/class/folder"
        val searchPathFile = createFileInTmpFolder("search_paths.txt")
        File(searchPathFile).writeText(defaultSearchPaths)

        SystemPropertySetter("search_paths_file", searchPathFile).use {
            SystemPropertySetter("jmeter.home", temporaryFolder.toString()).use {
                System.setProperty("search_paths_file", searchPathFile)

                val args = arrayOf("-Xssh", "server1", "server2", "-XadditionalSearchPaths", "bar.jar", "--", "-P", "proxy")
                val cli = LauncherCommandLine()
                CommandLine.populateCommand(cli, *args)
                val searchPaths = Launcher.prepareJMeterPropsGradle(cli)
                assertEquals("$defaultSearchPaths;bar.jar", searchPaths)
            }
        }
    }

    @Test
    fun testCommandLineMungingGradleTestExceptions() {
        val defaultSearchPaths = "/foo/bar/baz.jar;/some/class/folder"
        val searchPathFile = createFileInTmpFolder("search_paths.txt")
        File(searchPathFile).writeText(defaultSearchPaths)

        SystemPropertySetter("search_paths_file", searchPathFile).use {
            SystemPropertySetter("jmeter.home").use {
                val args = arrayOf("-Xssh", "server1", "server2", "-XadditionalSearchPaths", "bar.jar", "--", "-P", "proxy")
                val cli = LauncherCommandLine()
                CommandLine.populateCommand(cli, *args)
                testForException("System property jmeter.home must be set when running without capsule") { Launcher.prepareJMeterPropsGradle(cli) }
            }
        }

        SystemPropertySetter("search_paths_file").use {
            SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
                val args = arrayOf("-Xssh", "server1", "server2", "-XadditionalSearchPaths", "bar.jar", "--", "-P", "proxy")
                val cli = LauncherCommandLine()
                CommandLine.populateCommand(cli, *args)
                testForException("System property search_paths_file must be set when running without capsule") { Launcher.prepareJMeterPropsGradle(cli) }
            }
        }
    }

    @Test
    fun testRmiPortReading() {
        val rmiConfig = createFileInTmpFolder("rmi.conf")
        File(rmiConfig).writeText("#This is a comment\n \nfoo:21011\n   \ntest:5150")

        val result = Launcher.readHostAndPortMap(rmiConfig)
        assertEquals(mapOf("foo" to 21011, "test" to 5150), result)
    }

    private fun createFileInTmpFolder(name: String, content: String = ""): String {
        val file = (temporaryFolder.root.toPath() / name).toFile()
        file.writeText(content)
        return file.absolutePath
    }


    @Test
    fun testJMeterArgPreparationServer() {
        SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
            val defaultJmeterPropertiesFile = createFileInTmpFolder("jmeter.properties")
            createFileInTmpFolder("custom.properties")
            createFileInTmpFolder("server-rmi.config", "${InetAddress.getLocalHost().hostName}:10101")
            createFileInTmpFolder("nomatch-rmi.config", "notmatching:10101")

            val cmdLine = LauncherCommandLine()
            cmdLine.jMeterArguments.addAll(listOf("-s"))

            val result = Launcher.prepareJMeterArguments(cmdLine)
            assertEquals(defaultJmeterPropertiesFile, result.jmeterPropertiesFile.toString())
            assertEquals(mapOf(InetAddress.getLocalHost().hostName to 10101), result.serverRmiMappings)
            val expectedServerPropsFile = temporaryFolder.root.toPath() / "server-rmi.properties"
            assertEquals(listOf("-s", "-p", defaultJmeterPropertiesFile, "-q", expectedServerPropsFile.toString()), result.jmeterArgs)
            val serverPropsFile = expectedServerPropsFile.toFile()
            assertTrue(serverPropsFile.exists())
            assertEquals("server.rmi.localport=10101", serverPropsFile.readLines().first())
        }
    }

    @Test
    fun testJMeterArgPreparationServerCustomProperties() {
        SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
            createFileInTmpFolder("jmeter.properties")
            val customJmeterPropertiesFile = createFileInTmpFolder("custom.properties")
            createFileInTmpFolder("server-rmi.config", "${InetAddress.getLocalHost().hostName}:10101")
            val noMatchingServerRmiMapping = createFileInTmpFolder("nomatch-rmi.config", "notmatching:10101")

            val cmdLine = LauncherCommandLine()
            cmdLine.jMeterArguments.addAll(listOf("-s"))
            cmdLine.jMeterProperties = customJmeterPropertiesFile
            cmdLine.serverRmiMappings = noMatchingServerRmiMapping

            val result = Launcher.prepareJMeterArguments(cmdLine)
            assertEquals(customJmeterPropertiesFile, result.jmeterPropertiesFile.toString())
            assertEquals(mapOf("notmatching" to 10101), result.serverRmiMappings)
            val expectedServerPropsFile = temporaryFolder.root.toPath() / "server-rmi.properties"
            assertEquals(listOf("-s", "-p", customJmeterPropertiesFile), result.jmeterArgs)
            val serverPropsFile = expectedServerPropsFile.toFile()
            assertFalse(serverPropsFile.exists())
        }
    }

    @Test
    fun testJMeterArgPreparationClient() {
        SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
            val defaultJmeterPropertiesFile = createFileInTmpFolder("jmeter.properties")
            createFileInTmpFolder("custom.properties")
            createFileInTmpFolder("server-rmi.config", "${InetAddress.getLocalHost().hostName}:10101")
            createFileInTmpFolder("nomatch-rmi.config", "notmatching:10101")

            val cmdLine = LauncherCommandLine()

            val result = Launcher.prepareJMeterArguments(cmdLine)
            assertEquals(defaultJmeterPropertiesFile, result.jmeterPropertiesFile.toString())
            assertEquals(mapOf(InetAddress.getLocalHost().hostName to 10101), result.serverRmiMappings)
            val expectedServerPropsFile = temporaryFolder.root.toPath() / "server-rmi.properties"
            assertEquals(listOf("-p", defaultJmeterPropertiesFile), result.jmeterArgs)
            val serverPropsFile = expectedServerPropsFile.toFile()
            assertFalse(serverPropsFile.exists())
        }
    }

    @Test
    fun testJMeterArgPreparationClientCustomProperties() {
        SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
            createFileInTmpFolder("jmeter.properties")
            val customJmeterPropertiesFile = createFileInTmpFolder("custom.properties")
            createFileInTmpFolder("server-rmi.config", "${InetAddress.getLocalHost().hostName}:10101")
            val noMatchingServerRmiMapping = createFileInTmpFolder("nomatch-rmi.config", "notmatching:10101")

            val cmdLine = LauncherCommandLine()
            cmdLine.jMeterProperties = customJmeterPropertiesFile
            cmdLine.serverRmiMappings = noMatchingServerRmiMapping

            val result = Launcher.prepareJMeterArguments(cmdLine)
            assertEquals(customJmeterPropertiesFile, result.jmeterPropertiesFile.toString())
            assertEquals(mapOf("notmatching" to 10101), result.serverRmiMappings)
            val expectedServerPropsFile = temporaryFolder.root.toPath() / "server-rmi.properties"
            assertEquals(listOf("-p", customJmeterPropertiesFile), result.jmeterArgs)
            val serverPropsFile = expectedServerPropsFile.toFile()
            assertFalse(serverPropsFile.exists())
        }
    }

    @Test
    fun testJMeterArgPreparationException() {
        SystemPropertySetter("jmeter.home", temporaryFolder.root.toString()).use {
            createFileInTmpFolder("jmeter.properties")
            val customJmeterPropertiesFile = createFileInTmpFolder("custom.properties")
            createFileInTmpFolder("server-rmi.config", "${InetAddress.getLocalHost().hostName}:10101")
            val cmdLine = LauncherCommandLine()
            cmdLine.jMeterArguments.addAll(listOf("-p", customJmeterPropertiesFile))
            testForException("To choose jmeter.properties, use the -XjmeterProperties flag, not -p for JMeter arguments") { Launcher.prepareJMeterArguments(cmdLine) }
        }
    }

    class SystemPropertySetter(private val propertyName: String, private val propertyValue: String? = null) : Closeable {
        private val originalValue: String? = System.getProperty(propertyName).also { setOrRemove(propertyValue) }

        override fun close() {
            setOrRemove(originalValue)
        }

        private fun setOrRemove(value: String?) {
            if (value == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, value)
            }
        }
    }

    @Test
    fun testSystemPropertySetter() {
        val propertyName = "mytest.property"
        assertNull(System.getProperty(propertyName))
        SystemPropertySetter(propertyName, "foo").use {
            assertEquals("foo", System.getProperty(propertyName))
            SystemPropertySetter(propertyName, "bar").use {
                assertEquals("bar", System.getProperty(propertyName))

            }
            assertEquals("foo", System.getProperty(propertyName))
        }
        assertNull(System.getProperty(propertyName))
    }

    companion object {
        inline fun testForException(expectedMessage: String, block: () -> Unit) {
            var exceptionThrown = false
            try {
                block()
            } catch (e: Launcher.Companion.LauncherException) {
                exceptionThrown = true
                assertNotNull(e.message)
                assertTrue(e.message!!.contains(expectedMessage))
            }
            assertTrue(exceptionThrown, "Expected exception has not been thrown")
        }
    }
}

