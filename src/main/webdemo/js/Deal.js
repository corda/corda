"use strict"

let Deal = function(dealViewModel) {
    let now = new Date();
    let tradeId = `T${now.getUTCFullYear()}-${now.getUTCMonth()}-${now.getUTCDate()}.${now.getUTCHours()}:${now.getUTCMinutes()}:${now.getUTCSeconds()}:${now.getUTCMilliseconds()}`

    this.toJson = () => {
        let fixedLeg = {};
        let floatingLeg = {};
        let common = {};
        _.assign(fixedLeg, dealViewModel.fixedLeg);
        _.assign(floatingLeg, dealViewModel.floatingLeg);
        _.assign(common, dealViewModel.common);
        _.assign(fixedLeg.fixedRate, fixedRateViewModel);

        fixedLeg.fixedRate = Number(fixedLeg.fixedRate) / 100;

        common.tradeID = tradeId;
        common.eligibleCurrency = common.baseCurrency;
        common.independentAmounts.token = common.baseCurrency;
        common.threshold.token = common.baseCurrency;
        common.minimumTransferAmount.token = common.baseCurrency;
        common.rounding.token = common.baseCurrency;

        fixedLeg.notional.token = common.baseCurrency;
        fixedLeg.effectiveDate = formatDateForNode(fixedLeg.effectiveDate);
        fixedLeg.terminationDate = formatDateForNode(fixedLeg.terminationDate);
        fixedLeg.fixedRate = { ratioUnit: { value: fixedLeg.fixedRate } };
        fixedLeg.dayCountBasisDay = dayCountBasisLookup[fixedLeg.dayCountBasis].day;
        fixedLeg.dayCountBasisYear = dayCountBasisLookup[fixedLeg.dayCountBasis].year;
        delete fixedLeg.dayCountBasis;

        floatingLeg.notional.token = common.baseCurrency;
        floatingLeg.effectiveDate = formatDateForNode(floatingLeg.effectiveDate);
        floatingLeg.terminationDate = formatDateForNode(floatingLeg.terminationDate);
        floatingLeg.dayCountBasisDay = dayCountBasisLookup[floatingLeg.dayCountBasis].day;
        floatingLeg.dayCountBasisYear = dayCountBasisLookup[floatingLeg.dayCountBasis].year;
        delete floatingLeg.dayCountBasis;

        let json = {
            fixedLeg: fixedLeg,
            floatingLeg: floatingLeg,
            calculation: calculationModel,
            common: common
        }

        return json;
    };
};

define([], () => {
    return Deal;
})
