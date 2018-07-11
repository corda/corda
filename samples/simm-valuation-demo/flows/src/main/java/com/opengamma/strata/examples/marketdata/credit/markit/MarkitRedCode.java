/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.marketdata.credit.markit;

import com.google.common.base.Preconditions;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.TypedString;
import org.joda.convert.FromString;

/**
 * A simple string type to contain a 6 or 9 character Markit RED Code.
 * <p>
 * static utilities to convert from or to StandardIds with a fixed schema
 * <p>
 * http://www.markit.com/product/reference-data-cds
 */
public final class MarkitRedCode
        extends TypedString<MarkitRedCode> {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Scheme used in an OpenGamma {@link StandardId} where the value is a Markit RED code.
     */
    public static final String MARKIT_REDCODE_SCHEME = "MarkitRedCode";

    //-------------------------------------------------------------------------

    /**
     * Obtains an instance from the specified name.
     * <p>
     * RED codes must be 6 or 9 characters long.
     *
     * @param name  the name of the field
     * @return a RED code
     */
    @FromString
    public static MarkitRedCode of(String name) {
        ArgChecker.isTrue(name.length() == 6 || name.length() == 9, "RED Code must be exactly 6 or 9 characters");
        return new MarkitRedCode(name);
    }

    /**
     * Converts from a standard identifier ensuring the scheme is correct.
     *
     * @param id standard id identifying a RED code
     * @return the equivalent RED code
     */
    public static MarkitRedCode from(StandardId id) {
        Preconditions.checkArgument(id.getScheme().equals(MARKIT_REDCODE_SCHEME));
        return MarkitRedCode.of(id.getValue());
    }

    /**
     * Creates a standard identifier using the correct Markit RED code scheme.
     *
     * @param name  the Markit RED code, 6 or 9 characters long
     * @return the equivalent standard identifier
     */
    public static StandardId id(String name) {
        ArgChecker.isTrue(name.length() == 6 || name.length() == 9, "RED Code must be exactly 6 or 9 characters");
        return StandardId.of(MARKIT_REDCODE_SCHEME, name);
    }

    /**
     * Creates an instance.
     *
     * @param name  the RED code
     */
    private MarkitRedCode(String name) {
        super(name);
    }

    //-------------------------------------------------------------------------

    /**
     * Converts this RED code to a standard identifier.
     *
     * @return the standard identifier
     */
    public StandardId toStandardId() {
        return StandardId.of(MARKIT_REDCODE_SCHEME, getName());
    }

}
