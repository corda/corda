/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

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