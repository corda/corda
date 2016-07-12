'use strict';

define(['utils/dayCountBasisLookup'], (dayCountBasisLookup) => {
    return {
        fixedRatePayer: "Bank A",
        notional: {
            quantity: 2500000000
        },
        paymentFrequency: "Annual",
        effectiveDateAdjustment: null,
        terminationDateAdjustment: null,
        fixedRate: "1.676",
        dayCountBasis: dayCountBasisLookup["ACT/360"],
        rollConvention: "Following",
        dayInMonth: 10,
        paymentRule: "InArrears",
        paymentDelay: "0",
        paymentCalendar: "London",
        interestPeriodAdjustment: "Adjusted"
    };
});