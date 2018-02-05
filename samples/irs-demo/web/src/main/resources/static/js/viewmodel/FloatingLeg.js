'use strict';

define(['utils/dayCountBasisLookup'], function (dayCountBasisLookup) {
    return {
        floatingRatePayer: "O=Bank B,L=New York,C=US",
        notional: 2500000000,
        paymentFrequency: "Quarterly",
        effectiveDateAdjustment: null,
        terminationDateAdjustment: null,
        dayCountBasis: dayCountBasisLookup["ACT/360"],
        rollConvention: "ModifiedFollowing",
        fixingRollConvention: "ModifiedFollowing",
        dayInMonth: 10,
        resetDayInMonth: 10,
        paymentRule: "InArrears",
        paymentDelay: "0",
        interestPeriodAdjustment: "Adjusted",
        fixingPeriodOffset: 2,
        resetRule: "InAdvance",
        fixingsPerPayment: "Quarterly",
        indexSource: "Rates Service Provider",
        indexTenor: {
            name: "3M"
        }
    };
});