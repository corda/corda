'use strict';

define(['utils/dayCountBasisLookup'], (dayCountBasisLookup) => {
    return {
        floatingRatePayer: "Bank B",
        notional: {
           quantity: 2500000000
        },
        paymentFrequency: "Quarterly",
        effectiveDate: new Date(2016, 3, 11),
        effectiveDateAdjustment: null,
        terminationDate: new Date(2026, 3, 11),
        terminationDateAdjustment: null,
        dayCountBasis: dayCountBasisLookup["ACT/360"],
        rollConvention: "Following",
        fixingRollConvention: "Following",
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