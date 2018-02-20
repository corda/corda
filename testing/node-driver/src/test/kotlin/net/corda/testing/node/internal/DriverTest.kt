package net.corda.testing.node.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import kotlin.streams.toList

class DriverTest {

    @Test
    fun `driver rejects multiple nodes with the same name`() {

        driver(DriverParameters(startNodesInProcess = true)) {

            assertThatThrownBy { listOf(newNode(DUMMY_BANK_A_NAME)(), newNode(DUMMY_BANK_B_NAME)(), newNode(DUMMY_BANK_A_NAME)()).transpose().getOrThrow() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `driver rejects multiple nodes with the same name parallel`() {

        driver(DriverParameters(startNodesInProcess = true)) {

            val nodes = listOf(newNode(DUMMY_BANK_A_NAME), newNode(DUMMY_BANK_B_NAME), newNode(DUMMY_BANK_A_NAME))

            assertThatThrownBy { nodes.parallelStream().map { it.invoke() }.toList().transpose().getOrThrow() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `driver allows reusing names of nodes that have been stopped`() {

        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeA = newNode(DUMMY_BANK_A_NAME)().getOrThrow()

            nodeA.stop()

            assertThatCode { newNode(DUMMY_BANK_A_NAME)().getOrThrow() }.doesNotThrowAnyException()
        }
    }

    private fun DriverDSL.newNode(name: CordaX500Name) = { startNode(NodeParameters(providedName = name)) }
}