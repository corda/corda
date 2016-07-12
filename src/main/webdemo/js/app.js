"use strict"

function formatDateForNode(date) {
    // Produces yyyy-dd-mm. JS is missing proper date formatting libs
    let day = ("0" + (date.getDate())).slice(-2);
    let month = ("0" + (date.getMonth() + 1)).slice(-2);
    return `${date.getFullYear()}-${month}-${day}`;
}

function formatDateForAngular(dateStr) {
    let parts = dateStr.split("-");
    return new Date(parts[0], parts[1], parts[2]);
}

let fixedLegModel = {
    fixedRatePayer: "Bank A",
    notional: {
        quantity: 2500000000
    },
    paymentFrequency: "Annual",
    effectiveDateAdjustment: null,
    terminationDateAdjustment: null,
    fixedRate: "1.676",
    dayCountBasis: "30/360",
    //dayCountBasisDay: "D30",
    //dayCountBasisYear: "Y360",
    rollConvention: "Following",
    dayInMonth: 10,
    paymentRule: "InArrears",
    paymentDelay: "0",
    paymentCalendar: "London",
    interestPeriodAdjustment: "Adjusted"
};

let floatingLegModel = {
   floatingRatePayer: "Bank B",
   notional: {
       quantity: 2500000000
   },
   paymentFrequency: "Quarterly",
   effectiveDate: new Date(2016, 3, 11),
   effectiveDateAdjustment: null,
   terminationDate: new Date(2026, 3, 11),
   terminationDateAdjustment: null,
   dayCountBasis: "30/360",
   //dayCountBasisDay: "D30",
   //dayCountBasisYear: "Y360",
   rollConvention: "Following",
   fixingRollConvention: "Following",
   dayInMonth: 10,
   resetDayInMonth: 10,
   paymentRule: "InArrears",
   paymentDelay: "0",
   paymentCalendar: [ "London" ],
   interestPeriodAdjustment: "Adjusted",
   fixingPeriodOffset: 2,
   resetRule: "InAdvance",
   fixingsPerPayment: "Quarterly",
   fixingCalendar: [ "NewYork" ],
   index: "ICE LIBOR",
   indexSource: "Rates Service Provider",
   indexTenor: {
       name: "3M"
   }
};

let calculationModel = {
    expression: "( fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value)) -(floatingLeg.notional.quantity * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
    floatingLegPaymentSchedule: {

    },
    fixedLegPaymentSchedule: {

    }
};

let fixedRateViewModel = {
    ratioUnit: {
        value: 0.01 // %
    }
}

let commonViewModel = {
    baseCurrency: "EUR",
    effectiveDate: new Date(2016, 3, 11),
    terminationDate: new Date(2026, 3, 11),
    eligibleCreditSupport: "Cash in an Eligible Currency",
    independentAmounts: {
        quantity: 0
    },
    threshold: {
        quantity: 0
    },
    minimumTransferAmount: {
        quantity: 25000000
    },
    rounding: {
        quantity: 1000000
    },
    valuationDate: "Every Local Business Day",
    notificationTime: "2:00pm London",
    resolutionTime: "2:00pm London time on the first LocalBusiness Day following the date on which the notice is give",
    interestRate: {
        oracle: "Rates Service Provider",
        tenor: {
            name: "6M"
        },
        ratioUnit: null,
        name: "EONIA"
    },
    addressForTransfers: "",
    exposure: {},
    localBusinessDay: [ "London" , "NewYork" ],
    dailyInterestAmount: "(CashAmount * InterestRate ) / (fixedLeg.notional.token.currencyCode.equals('GBP')) ? 365 : 360",
    hashLegalDocs: "put hash here"
};

let dealViewModel = {
  fixedLeg: fixedLegModel,
  floatingLeg: floatingLegModel,
  common: commonViewModel
};

// TODO: Fill out this lookup table and use it to inject into the view.
let dayCountBasisLookup = {
    "30/360": {
        "day": "D30",
        "year": "Y360"
    }
}

define([
    'angular',
    'angularRoute',
    'jquery',
    'fcsaNumber',
    'semantic'
],
(angular, angularRoute, $, fcsaNumber, semantic) => {
    angular.module('irsViewer', ['ngRoute', 'fcsa-number']);
    requirejs(['routes']);
});