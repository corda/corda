'use strict';

define([], () => {
    return {
        fixedRatePayer: "Bank A",
        notional: {
            quantity: 2500000000
        },
        paymentFrequency: "Annual",
        effectiveDateAdjustment: null,
        terminationDateAdjustment: null,
        fixedRate: "1.676",
        dayCountBasis: "30/360",
        rollConvention: "Following",
        dayInMonth: 10,
        paymentRule: "InArrears",
        paymentDelay: "0",
        paymentCalendar: "London",
        interestPeriodAdjustment: "Adjusted"
    };
});