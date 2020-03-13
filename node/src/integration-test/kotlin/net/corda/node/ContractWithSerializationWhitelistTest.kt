package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.serialization.whitelist.WhitelistData
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.serialization.whitelist.WhitelistFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
@Suppress("FunctionName")
class ContractWithSerializationWhitelistTest(private val runInProcess: Boolean) {
    companion object {
        const val DATA = 123456L

        @JvmField
        val logger = loggerFor<ContractWithSerializationWhitelistTest>()

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.serialization.whitelist").signed()

        @JvmField
        val workflowCordapp = cordappWithPackages("net.corda.flows.serialization.whitelist").signed()

        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()

        fun parametersFor(runInProcess: Boolean): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                systemProperties = security.systemProperties,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(contractCordapp, workflowCordapp)
            )
        }

        @Parameters
        @JvmStatic
        fun modes(): List<Array<Boolean>> = listOf(Array(1) { true }, Array(1) { false })

        @BeforeClass
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<WhitelistData>()
        }
    }

    @Test(timeout = 300_000)
    fun `test serialization whitelist`() {
        logger.info("RUN-IN-PROCESS=$runInProcess")

        val user = User("u", "p", setOf(Permissions.all()))
        driver(parametersFor(runInProcess = runInProcess)) {
            val badData = WhitelistData(DATA)
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<ContractRejection> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::WhitelistFlow, badData)
                            .returnValue
                            .getOrThrow()
                    }
            }
            assertThat(ex)
                .hasMessageContaining("WhitelistData $badData exceeds maximum value!")
        }
    }
}
