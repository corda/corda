/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.examples.marketdata.credit.markit;

import com.opengamma.strata.product.credit.SeniorityLevel;

/**
 * Specifies the repayment precedence of a debt instrument.
 * <p>
 * This is also known as the <i>RED Tier Code</i>.
 */
public enum MarkitSeniorityLevel {

    /**
     * Senior domestic.
     */
    SECDOM,

    /**
     * Senior foreign.
     */
    SNRFOR,

    /**
     * Subordinate, Lower Tier 2.
     */
    SUBLT2,

    /**
     * Subordinate Tier 1.
     */
    PREFT1,

    /**
     * Subordinate, Upper Tier 2.
     */
    JRSUBUT2;

    //-------------------------------------------------------------------------

    /**
     * Converts Markit code to standard seniority level.
     *
     * @return the converted level
     */
    public SeniorityLevel translate() {
        switch (this) {
            case SECDOM:
                return SeniorityLevel.SENIOR_SECURED_DOMESTIC;
            case SNRFOR:
                return SeniorityLevel.SENIOR_UNSECURED_FOREIGN;
            case SUBLT2:
                return SeniorityLevel.SUBORDINATE_LOWER_TIER_2;
            case PREFT1:
                return SeniorityLevel.SUBORDINATE_TIER_1;
            case JRSUBUT2:
                return SeniorityLevel.SUBORDINATE_UPPER_TIER_2;
            default:
                throw new IllegalStateException("Unmapped seniority level. Do not have mapping for " + this);
        }
    }

    /**
     * Converts seniority level to Markit equivalent.
     *
     * @param seniorityLevel  the level to convert
     * @return the converted level
     */
    public static MarkitSeniorityLevel from(SeniorityLevel seniorityLevel) {
        switch (seniorityLevel) {
            case SENIOR_SECURED_DOMESTIC:
                return SECDOM;
            case SENIOR_UNSECURED_FOREIGN:
                return SNRFOR;
            case SUBORDINATE_LOWER_TIER_2:
                return SUBLT2;
            case SUBORDINATE_TIER_1:
                return PREFT1;
            case SUBORDINATE_UPPER_TIER_2:
                return JRSUBUT2;
            default:
                throw new IllegalArgumentException("Unknown seniority level. Do not have mapping for " + seniorityLevel);
        }
    }

}
