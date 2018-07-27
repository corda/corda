package net.corda.core.contracts;

import org.junit.Test;

import static net.corda.finance.Currencies.POUNDS;
import static org.junit.Assert.assertEquals;

public class AmountParsingTest {

    @Test
    public void testGbpParse() {
        assertEquals(POUNDS(10), Amount.parseCurrency("10 GBP"));
        assertEquals(POUNDS(11), Amount.parseCurrency("\u00A311")); // £
    }
}
