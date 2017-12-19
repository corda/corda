package net.corda.core.contracts

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.TestIdentity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail


class ContractsDSLTests {
    class UnwantedCommand : CommandData

    interface TestCommands : CommandData {
        class CommandOne : TypeOnlyCommandData(), TestCommands
        class CommandTwo : TypeOnlyCommandData(), TestCommands
    }

    private companion object {

        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MEGA_CORP_PARTY get() = MEGA_CORP.party
        val MEGA_CORP_KEY get() = MEGA_CORP.publicKey
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val MINI_CORP_PARTY get() = MINI_CORP.party
        val MINI_CORP_KEY get() = MINI_CORP.publicKey

        val VALID_COMMAND_ONE = CommandWithParties(listOf(MEGA_CORP_KEY, MINI_CORP_KEY), listOf(MEGA_CORP_PARTY, MINI_CORP_PARTY), TestCommands.CommandOne())
        val VALID_COMMAND_TWO = CommandWithParties(listOf(MEGA_CORP_KEY), listOf(MEGA_CORP_PARTY), TestCommands.CommandTwo())
        val INVALID_COMMAND = CommandWithParties(emptyList(), emptyList(), UnwantedCommand())

    }

    @RunWith(Parameterized::class)
    class RequireSingleCommandTests(private val testFunction: (Collection<CommandWithParties<CommandData>>) -> CommandWithParties<CommandData>, description: String) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{1}")
            fun data(): Collection<Array<Any>> = listOf(
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>> -> commands.requireSingleCommand<TestCommands>() }, "Inline version"),
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>> -> commands.requireSingleCommand(TestCommands::class.java) }, "Interop version")
            )
        }

        @Test
        fun `check function returns one value`() {
            val commands = listOf(VALID_COMMAND_ONE, INVALID_COMMAND)
            val returnedCommand = testFunction(commands)
            assertEquals(returnedCommand, VALID_COMMAND_ONE, "they should be the same")
        }

        @Test(expected = IllegalArgumentException::class)
        fun `check error is thrown if more than one valid command`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            testFunction(commands)
        }

        @Test
        fun `check error is thrown when command is of wrong type`() {
            val commands = listOf(INVALID_COMMAND)
            try {
                testFunction(commands)
            } catch (e: IllegalStateException) {
                assertEquals(e.message, "Required net.corda.core.contracts.ContractsDSLTests.TestCommands command")
                return
            }
            fail("Should have returned an exception")
        }
    }

    @RunWith(Parameterized::class)
    class SelectWithSingleInputsTests(private val testFunction: (Collection<CommandWithParties<CommandData>>, PublicKey?, AbstractParty?) -> Iterable<CommandWithParties<CommandData>>, description: String) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{1}")
            fun data(): Collection<Array<Any>> = listOf(
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>>, signer: PublicKey?, party: AbstractParty? -> commands.select<TestCommands>(signer, party) }, "Inline version"),
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>>, signer: PublicKey?, party: AbstractParty? -> commands.select(TestCommands::class.java, signer, party) }, "Interop version")
            )
        }

        @Test
        fun `check that function returns all values`() {
            val commands = listOf (VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            testFunction(commands, null, null)
            assertEquals(2, commands.size)
            assertTrue(commands.contains(VALID_COMMAND_ONE))
            assertTrue(commands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function does not return invalid command types`() {
            val commands = listOf(VALID_COMMAND_ONE, INVALID_COMMAND)
            val filteredCommands = testFunction(commands, null, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(INVALID_COMMAND))
        }

        @Test
        fun `check that function returns commands from valid signers`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, MINI_CORP_KEY, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function returns commands from valid parties`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, null, MINI_CORP_PARTY).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(VALID_COMMAND_TWO))
        }
    }

    @RunWith(Parameterized::class)
    class SelectWithMultipleInputsTests(private val testFunction: (Collection<CommandWithParties<CommandData>>, Collection<PublicKey>?, Collection<Party>?) -> Iterable<CommandWithParties<CommandData>>, description: String) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{1}")
            fun data(): Collection<Array<Any>> = listOf(
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>>, signers: Collection<PublicKey>?, party: Collection<Party>? -> commands.select<TestCommands>(signers, party) }, "Inline version"),
                    arrayOf<Any>({ commands: Collection<CommandWithParties<CommandData>>, signers: Collection<PublicKey>?, party: Collection<Party>? -> commands.select(TestCommands::class.java, signers, party) }, "Interop version")
            )
        }

        @Test
        fun `check that function returns all values`() {
            val commands = listOf (VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            testFunction(commands, null, null)
            assertEquals(2, commands.size)
            assertTrue(commands.contains(VALID_COMMAND_ONE))
            assertTrue(commands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function does not return invalid command types`() {
            val commands = listOf(VALID_COMMAND_ONE, INVALID_COMMAND)
            val filteredCommands = testFunction(commands, null, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(INVALID_COMMAND))
        }

        @Test
        fun `check that function returns commands from valid signers`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, listOf(MEGA_CORP_KEY), null).toList()
            assertEquals(2, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertTrue(filteredCommands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function returns commands from all valid signers`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, listOf(MINI_CORP_KEY, MEGA_CORP_KEY), null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function returns commands from valid parties`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, null, listOf(MEGA_CORP_PARTY)).toList()
            assertEquals(2, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertTrue(filteredCommands.contains(VALID_COMMAND_TWO))
        }

        @Test
        fun `check that function returns commands from all valid parties`() {
            val commands = listOf(VALID_COMMAND_ONE, VALID_COMMAND_TWO)
            val filteredCommands = testFunction(commands, null, listOf(MINI_CORP_PARTY, MEGA_CORP_PARTY)).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(VALID_COMMAND_ONE))
            assertFalse(filteredCommands.contains(VALID_COMMAND_TWO))
        }
    }
}