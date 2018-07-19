package net.corda.core.contracts

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractsDSLTests {
    class UnwantedCommand : CommandData

    interface TestCommands : CommandData {
        class CommandOne : TypeOnlyCommandData(), TestCommands
        class CommandTwo : TypeOnlyCommandData(), TestCommands
    }

    private companion object {
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))

        val validCommandOne = CommandWithParties(listOf(megaCorp.publicKey, miniCorp.publicKey), listOf(megaCorp.party, miniCorp.party), TestCommands.CommandOne())
        val validCommandTwo = CommandWithParties(listOf(megaCorp.publicKey), listOf(megaCorp.party), TestCommands.CommandTwo())
        val invalidCommand = CommandWithParties(emptyList(), emptyList(), UnwantedCommand())
    }

    @RunWith(Parameterized::class)
    class RequireSingleCommandTests(private val testFunction: (Collection<CommandWithParties<CommandData>>) -> CommandWithParties<CommandData>,
                                    @Suppress("UNUSED_PARAMETER") description: String) {
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
            val commands = listOf(validCommandOne, invalidCommand)
            val returnedCommand = testFunction(commands)
            assertEquals(returnedCommand, validCommandOne, "they should be the same")
        }

        @Test(expected = IllegalArgumentException::class)
        fun `check error is thrown if more than one valid command`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            testFunction(commands)
        }

        @Test
        fun `check error is thrown when command is of wrong type`() {
            val commands = listOf(invalidCommand)
            Assertions.assertThatThrownBy { testFunction(commands) }
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("Required net.corda.core.contracts.ContractsDSLTests.TestCommands command")
        }
    }

    @RunWith(Parameterized::class)
    class SelectWithSingleInputsTests(private val testFunction: (Collection<CommandWithParties<CommandData>>, PublicKey?, AbstractParty?) -> Iterable<CommandWithParties<CommandData>>,
                                      @Suppress("UNUSED_PARAMETER") description: String) {
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
            val commands = listOf(validCommandOne, validCommandTwo)
            testFunction(commands, null, null)
            assertEquals(2, commands.size)
            assertTrue(commands.contains(validCommandOne))
            assertTrue(commands.contains(validCommandTwo))
        }

        @Test
        fun `check that function does not return invalid command types`() {
            val commands = listOf(validCommandOne, invalidCommand)
            val filteredCommands = testFunction(commands, null, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(invalidCommand))
        }

        @Test
        fun `check that function returns commands from valid signers`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, miniCorp.publicKey, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(validCommandTwo))
        }

        @Test
        fun `check that function returns commands from valid parties`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, null, miniCorp.party).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(validCommandTwo))
        }
    }

    @RunWith(Parameterized::class)
    class SelectWithMultipleInputsTests(private val testFunction: (Collection<CommandWithParties<CommandData>>, Collection<PublicKey>?, Collection<Party>?) -> Iterable<CommandWithParties<CommandData>>,
                                        @Suppress("UNUSED_PARAMETER") description: String) {
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
            val commands = listOf(validCommandOne, validCommandTwo)
            testFunction(commands, null, null)
            assertEquals(2, commands.size)
            assertTrue(commands.contains(validCommandOne))
            assertTrue(commands.contains(validCommandTwo))
        }

        @Test
        fun `check that function does not return invalid command types`() {
            val commands = listOf(validCommandOne, invalidCommand)
            val filteredCommands = testFunction(commands, null, null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(invalidCommand))
        }

        @Test
        fun `check that function returns commands from valid signers`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, listOf(megaCorp.publicKey), null).toList()
            assertEquals(2, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertTrue(filteredCommands.contains(validCommandTwo))
        }

        @Test
        fun `check that function returns commands from all valid signers`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, listOf(miniCorp.publicKey, megaCorp.publicKey), null).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(validCommandTwo))
        }

        @Test
        fun `check that function returns commands from valid parties`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, null, listOf(megaCorp.party)).toList()
            assertEquals(2, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertTrue(filteredCommands.contains(validCommandTwo))
        }

        @Test
        fun `check that function returns commands from all valid parties`() {
            val commands = listOf(validCommandOne, validCommandTwo)
            val filteredCommands = testFunction(commands, null, listOf(miniCorp.party, megaCorp.party)).toList()
            assertEquals(1, filteredCommands.size)
            assertTrue(filteredCommands.contains(validCommandOne))
            assertFalse(filteredCommands.contains(validCommandTwo))
        }
    }
}