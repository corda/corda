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
