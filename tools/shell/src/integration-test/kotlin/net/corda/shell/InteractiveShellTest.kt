package net.corda.shell

import com.google.common.io.Files
import net.corda.client.rpc.internal.createCordaRPCClientWithSslAndClassLoader
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test

class InteractiveShellTest {

    @Test
    fun `should not log in with invalid credentials`() {
        val user = User("u", "p", setOf())

        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = net.corda.shell.ShellConfiguration(Files.createTempDir().toPath(),
                    "fake", "fake",
                    node.rpcAddress,
                    null, null, false)
//
            try {
                InteractiveShell.startShell(conf,
                        { username: String?, credentials: String? ->
                            val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort)
                            client.start(username ?: "", credentials ?: "").proxy
                        })
                InteractiveShell.checkConnection()
                kotlin.test.fail("Should have failed at checkConnection")
            } catch (e: Exception) {
//                no idea
            }
        }
    }

//    extract InteractiveShell to not be so static
//    make use of noLocalShell in AbstractNode
//    test SSL standaloneshell
//    test ssh into standalone

    @Test
    fun `should log in with valid crentials`() {
        val user = User("u", "p", setOf())

        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = net.corda.shell.ShellConfiguration(Files.createTempDir().toPath(),
                    user.username, user.password,
                    node.rpcAddress,
                    null, null, false)

            InteractiveShell.startShell(conf,
                    { username: String?, credentials: String? ->
                        val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort)
                        client.start(username ?: "", credentials ?: "").proxy
                    })
            InteractiveShell.checkConnection()
        }
    }
}