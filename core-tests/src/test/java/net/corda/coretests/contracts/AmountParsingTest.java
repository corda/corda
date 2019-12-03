package net.corda.coretests.contracts;

import net.corda.core.contracts.Amount;
import org.junit.Test;

import static net.corda.finance.Currencies.POUNDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AmountParsingTest {

    @Test
    public void testGbpParse() {
        assertEquals(POUNDS(10), Amount.parseCurrency("10 GBP"));
        assertEquals(POUNDS(11), Amount.parseCurrency("£11"));
        fail();
    }
}
