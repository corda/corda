/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.marketdata;

import com.opengamma.strata.collect.io.IniFile;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.report.trade.TradeReportTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Contains utilities for working with data in the examples environment.
 */
public final class ExampleData {

    /**
     * Restricted constructor.
     */
    private ExampleData() {
    }

    //-------------------------------------------------------------------------

    /**
     * Loads a golden copy of expected results from a text file.
     *
     * @param name  the name of the results
     * @return the loaded results
     */
    public static String loadExpectedResults(String name) {
        String classpathResourceName = String.format(Locale.ENGLISH, "classpath:goldencopy/%s.txt", name);
        ResourceLocator resourceLocator = ResourceLocator.of(classpathResourceName);
        try {
            return resourceLocator.getCharSource().read().trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(name, ex);
        }
    }

    /**
     * Loads a trade report template from the standard INI format.
     *
     * @param templateName  the name of the template
     * @return the loaded report template
     */
    public static TradeReportTemplate loadTradeReportTemplate(String templateName) {
        String resourceName = String.format(Locale.ENGLISH, "classpath:example-reports/%s.ini", templateName);
        ResourceLocator resourceLocator = ResourceLocator.of(resourceName);
        IniFile ini = IniFile.of(resourceLocator.getCharSource());
        return TradeReportTemplate.load(ini);
    }

}
