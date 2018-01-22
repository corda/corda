package net.corda.core.serialization

import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CommandsSerializationTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `test cash move serialization`() {
        val command = Cash.Commands.Move(CommercialPaper::class.java)
        val copiedCommand = command.serialize().deserialize()

        assertEquals(command, copiedCommand)
    }
}