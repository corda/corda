package net.corda.core

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.jar.JarOutputStream

class PluginRegistrationTest {
    private companion object {
        val pluginJarFile = PluginRegistrationTest::class.java.getResource("/dummydriver.jar")!!.path
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test plugin registration` () {
        // Create node jarDir with an empty jar file in it
        val jarDir = temporaryFolder.newFolder("jardir").path!!
        JarOutputStream(FileOutputStream((jarDir / "empty.jar").toFile())).close ()
        val notaryConfig = NodeConfig(
                legalName = CordaX500Name("Notary", "Zurich", "CH"),
                isNotary = true,
                p2pPort = 30200,
                rpcPort = 30201,
                rpcAdminPort = 30203,
                users = listOf(User("_", "_", setOf("ALL"))))
        val config = NodeConfig(
                legalName = CordaX500Name(organisation = "org", locality = "Madrid", country = "ES"),
                p2pPort = 30100,
                rpcPort = 30101,
                rpcAdminPort = 30103,
                isNotary = false,
                users = listOf(User("_", "_", setOf("ALL"))),
                jarDirs = listOf(jarDir))

        // Check we do not have plugin on classpath
        assertThatCode { Class.forName("net.corda.smoketesting.plugins.DummyJDBCDriver") }.isInstanceOf(ClassNotFoundException::class.java)

        // Install plugin Jars in node directory, then start the node and close it
        val consoleOutput = temporaryFolder.newFile("node-stdout.txt")
        val nodeJvmArgs = arrayOf("--logging-level", "DEBUG", "--no-local-shell", "--log-to-console")
        val nodeFactory = NodeProcess.Factory(extraJvmArgs = nodeJvmArgs, redirectConsoleTo = consoleOutput)
                .setupPlugins(config, listOf(pluginJarFile))

        val notary = nodeFactory.create(notaryConfig)
        val node = nodeFactory.create(config)

        notary.close()
        node.close()

        val outputLines = consoleOutput.readLines()
        val classpath = outputLines.filter { it.contains(" classpath:") }.last()

        // If DummyJdbcDriver has been registered it should have printed a message
        require(outputLines.count { it.contains("[DummyJDBCDriver] hello") } > 0) {
            "Cannot find registration message from installed jdbc driver"
        }

        // Check the printed classpath contains 'nodeJarDir'
        require(classpath.contains(jarDir)) {
            "Expected to find $jarDir in printed classpath"
        }
    }
}

