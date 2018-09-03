package com.opengamma.strata.examples.marketdata;

/**
 * Contains utilities for using example market data.
 */
public final class ExampleMarketData {

    /**
     * Root resource directory of the built-in example market data
     */
    private static final String EXAMPLE_MARKET_DATA_ROOT = "example-marketdata";

    /**
     * Restricted constructor.
     */
    private ExampleMarketData() {
    }

    //-------------------------------------------------------------------------

    /**
     * Gets a market data builder for the built-in example market data.
     *
     * @return the market data builder
     */
    public static ExampleMarketDataBuilder builder() {
        return ExampleMarketDataBuilder.ofResource(EXAMPLE_MARKET_DATA_ROOT);
    }

}
