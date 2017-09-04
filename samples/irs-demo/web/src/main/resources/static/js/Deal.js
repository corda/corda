"use strict";

define(['viewmodel/FixedRate'], (fixedRateViewModel) => {
    let calculationModel = {
        expression: "( fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.quantity * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
        floatingLegPaymentSchedule: {

        },
        fixedLegPaymentSchedule: {

        }
    };

    let indexLookup = {
        "GBP": "ICE LIBOR",
        "USD": "ICE LIBOR",
        "EUR": "EURIBOR"
    };

    let calendarLookup = {
        "GBP": "London",
        "USD": "NewYork",
        "EUR": "London"
    };

    let Deal = function(dealViewModel) {
        let now = new Date();
        let tradeId = `T${now.getUTCFullYear()}-${now.getUTCMonth()}-${now.getUTCDate()}.${now.getUTCHours()}:${now.getUTCMinutes()}:${now.getUTCSeconds()}:${now.getUTCMilliseconds()}`;

        this.toJson = () => {
            let fixedLeg = {};
            let floatingLeg = {};
            let common = {};
            _.assign(fixedLeg, dealViewModel.fixedLeg);
            _.assign(floatingLeg, dealViewModel.floatingLeg);
            _.assign(common, dealViewModel.common);
            _.assign(fixedLeg.fixedRate, fixedRateViewModel);

            fixedLeg.fixedRate = Number(fixedLeg.fixedRate) / 100;
            fixedLeg.notional = fixedLeg.notional + ' ' + common.baseCurrency;
            fixedLeg.effectiveDate = formatDateForNode(common.effectiveDate);
            fixedLeg.terminationDate = formatDateForNode(common.terminationDate);
            fixedLeg.fixedRate = { ratioUnit: { value: fixedLeg.fixedRate } };
            fixedLeg.dayCountBasisDay = fixedLeg.dayCountBasis.day;
            fixedLeg.dayCountBasisYear = fixedLeg.dayCountBasis.year;
            fixedLeg.paymentCalendar = calendarLookup[common.baseCurrency];
            delete fixedLeg.dayCountBasis;

            floatingLeg.notional = floatingLeg.notional + ' ' + common.baseCurrency;
            floatingLeg.effectiveDate = formatDateForNode(common.effectiveDate);
            floatingLeg.terminationDate = formatDateForNode(common.terminationDate);
            floatingLeg.dayCountBasisDay = floatingLeg.dayCountBasis.day;
            floatingLeg.dayCountBasisYear = floatingLeg.dayCountBasis.year;
            floatingLeg.index = indexLookup[common.baseCurrency];
            floatingLeg.fixingCalendar = [calendarLookup[common.baseCurrency]];
            floatingLeg.paymentCalendar = [calendarLookup[common.baseCurrency]];
            delete floatingLeg.dayCountBasis;

            common.tradeID = tradeId;
            common.eligibleCurrency = common.baseCurrency;
            common.independentAmounts.token = common.baseCurrency;
            common.threshold.token = common.baseCurrency;
            common.minimumTransferAmount.token = common.baseCurrency;
            common.rounding.token = common.baseCurrency;
            delete common.effectiveDate;
            delete common.terminationDate;

            let json = {
                fixedLeg: fixedLeg,
                floatingLeg: floatingLeg,
                calculation: calculationModel,
                common: common,
                oracle: dealViewModel.oracle
            };

            return json;
        };
    };
    return Deal;
});
