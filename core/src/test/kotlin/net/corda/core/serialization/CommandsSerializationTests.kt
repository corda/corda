package net.corda.core.serialization

import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import kotlin.test.assertEquals

class CommandsSerializationTests : TestDependencyInjectionBase() {

    @Test
    fun `test cash move serialization`() {
        val command = Cash.Commands.Move(CommercialPaper::class.java)
        val copiedCommand = command.serialize().deserialize()

        assertEquals(command, copiedCommand)
    }
}