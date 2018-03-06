/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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