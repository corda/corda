/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.junit.Test
import kotlin.test.assertEquals

class EmbeddedExpressionsTest {
    @Test
    fun basic() {
        val jexl = JexlBuilder().create()
        val result = jexl.createExpression("2 + 2").evaluate(MapContext())
        assertEquals(4, result)
    }
}
