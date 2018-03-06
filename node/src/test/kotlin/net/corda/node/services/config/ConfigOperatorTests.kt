/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.config

import com.typesafe.config.ConfigFactory
import net.corda.nodeapi.internal.config.toProperties
import org.junit.Test
import kotlin.test.assertEquals

class ConfigOperatorTests {

    @Test
    fun `config plus behaves the same as map plus`() {
        val config = arrayOf("x" to "y1", "a" to "b", "z" to "Z")
        val overrides = arrayOf("x" to "y2", "c" to "d", "z" to null)
        val old = ConfigFactory.parseMap(mapOf(*config) + mapOf(*overrides))
        val new = configOf(*config) + mapOf(*overrides)
        listOf(old, new).map { it.toProperties() }.forEach { c ->
            assertEquals("y2", c["x"])
            assertEquals("b", c["a"])
            assertEquals("d", c["c"])
            assertEquals(null, c["z"])
        }
    }

}
