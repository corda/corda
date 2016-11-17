"use strict";
var FixedLegModel_1 = require('./model/FixedLegModel');
var FloatingLegModel_1 = require('./model/FloatingLegModel');
var CommonModel_1 = require('./model/CommonModel');
var _ = require('underscore');
var calculationModel = {
    expression: "( fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.quantity * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
    floatingLegPaymentSchedule: {},
    fixedLegPaymentSchedule: {}
};
var fixedRateModel = {
    ratioUnit: {
        value: 0.01 // %
    }
};
var indexLookup = {
    "GBP": "ICE LIBOR",
    "USD": "ICE LIBOR",
    "EUR": "EURIBOR"
};
var calendarLookup = {
    "GBP": "London",
    "USD": "NewYork",
    "EUR": "London"
};
var now = function () {
    return new Date();
};
// Copy the value of the field from b to a if it exists on both objects.
var unionMerge = function (a, b) {
    for (var key in b) {
        if (a.hasOwnProperty(key)) {
            a[key] = b[key];
        }
    }
};
var Deal = (function () {
    function Deal(dealViewModel, nodeService, irsService) {
        var _this = this;
        this.dealViewModel = dealViewModel;
        this.nodeService = nodeService;
        this.irsService = irsService;
        this.tradeId = "T" + now().getUTCFullYear() + "-" + now().getUTCMonth() + "-" + now().getUTCDate() + "." + now().getUTCHours() + ":" + now().getUTCMinutes() + ":" + now().getUTCSeconds() + ":" + now().getUTCMilliseconds();
        this.toFixedLegModel = function (fixedLegVM, commonVM) {
            var fixedLeg = new FixedLegModel_1.FixedLegModel();
            unionMerge(fixedLeg, fixedLegVM);
            fixedLeg.notional.token = commonVM.baseCurrency;
            fixedLeg.effectiveDate = commonVM.effectiveDate;
            fixedLeg.terminationDate = commonVM.terminationDate;
            fixedLeg.fixedRate = { ratioUnit: { value: Number(fixedLegVM.fixedRate) / 100 } };
            fixedLeg.dayCountBasisDay = _this.irsService.lookupDayCountBasis(fixedLegVM.dayCountBasis).day;
            fixedLeg.dayCountBasisYear = _this.irsService.lookupDayCountBasis(fixedLegVM.dayCountBasis).year;
            fixedLeg.paymentCalendar = calendarLookup[commonVM.baseCurrency];
            return fixedLeg;
        };
        this.toFloatingLegModel = function (floatingLegVM, commonVM) {
            var floatingLeg = new FloatingLegModel_1.FloatingLegModel();
            unionMerge(floatingLeg, floatingLegVM);
            floatingLeg.notional.token = commonVM.baseCurrency;
            floatingLeg.effectiveDate = commonVM.effectiveDate;
            floatingLeg.terminationDate = commonVM.terminationDate;
            floatingLeg.dayCountBasisDay = _this.irsService.lookupDayCountBasis(floatingLegVM.dayCountBasis).day;
            floatingLeg.dayCountBasisYear = _this.irsService.lookupDayCountBasis(floatingLegVM.dayCountBasis).year;
            floatingLeg.index = indexLookup[commonVM.baseCurrency];
            floatingLeg.fixingCalendar = [calendarLookup[commonVM.baseCurrency]];
            floatingLeg.paymentCalendar = [calendarLookup[commonVM.baseCurrency]];
            return floatingLeg;
        };
        this.toCommonModel = function (commonVM) {
            var common = new CommonModel_1.CommonModel();
            unionMerge(common, commonVM);
            common.tradeID = _this.tradeId;
            common.eligibleCurrency = commonVM.baseCurrency;
            common.independentAmounts.token = commonVM.baseCurrency;
            common.threshold.token = commonVM.baseCurrency;
            common.minimumTransferAmount.token = commonVM.baseCurrency;
            common.rounding.token = commonVM.baseCurrency;
            return common;
        };
        this.toJson = function () {
            var commonVM = _this.dealViewModel.common;
            var floatingLegVM = _this.dealViewModel.floatingLeg;
            var fixedLegVM = _this.dealViewModel.fixedLeg;
            var fixedLeg = _this.toFixedLegModel(fixedLegVM, commonVM);
            var floatingLeg = _this.toFloatingLegModel(floatingLegVM, commonVM);
            var common = _this.toCommonModel(commonVM);
            _.assign(fixedLeg.fixedRate, fixedRateModel);
            var json = {
                fixedLeg: fixedLeg,
                floatingLeg: floatingLeg,
                calculation: calculationModel,
                common: common
            };
            return json;
        };
    }
    return Deal;
}());
exports.Deal = Deal;
;
//# sourceMappingURL=Deal.js.map