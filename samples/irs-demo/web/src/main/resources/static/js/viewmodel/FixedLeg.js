'use strict';

define(['utils/dayCountBasisLookup'], function (dayCountBasisLookup) {
    return {
        fixedRatePayer: "O=Bank A,L=London,C=GB",
        notional: 2500000000,
        paymentFrequency: "SemiAnnual",
        effectiveDateAdjustment: null,
        terminationDateAdjustment: null,
        fixedRate: "1.676",
        dayCountBasis: dayCountBasisLookup["ACT/360"],
        rollConvention: "ModifiedFollowing",
        dayInMonth: 10,
        paymentRule: "InArrears",
        paymentDelay: "0",
        interestPeriodAdjustment: "Adjusted"
    };
});