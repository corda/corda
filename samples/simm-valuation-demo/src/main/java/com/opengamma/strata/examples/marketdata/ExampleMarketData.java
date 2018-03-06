/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
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
