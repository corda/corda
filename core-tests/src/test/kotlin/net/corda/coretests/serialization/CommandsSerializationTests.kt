package net.corda.coretests.serialization

import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Move
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CommandsSerializationTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test(timeout=300_000)
	fun `test cash move serialization`() {
        val command = Move(CommercialPaper::class.java)
        val copiedCommand = command.serialize().deserialize()

        assertEquals(command, copiedCommand)
    }
}