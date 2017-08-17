"use strict";
var CommonViewModel = (function () {
    function CommonViewModel() {
        this.baseCurrency = "EUR";
        this.effectiveDate = "2016-02-11";
        this.terminationDate = "2026-02-11";
        this.eligibleCreditSupport = "Cash in an Eligible Currency";
        this.independentAmounts = {
            quantity: 0
        };
        this.threshold = {
            quantity: 0
        };
        this.minimumTransferAmount = {
            quantity: 25000000
        };
        this.rounding = {
            quantity: 1000000
        };
        this.valuationDate = "Every Local Business Day";
        this.notificationTime = "2:00pm London";
        this.resolutionTime = "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given";
        this.interestRate = {
            oracle: "Rates Service Provider",
            tenor: {
                name: "6M"
            },
            ratioUnit: null,
            name: "EONIA"
        };
        this.addressForTransfers = "";
        this.exposure = {};
        this.localBusinessDay = ["London", "NewYork"];
        this.dailyInterestAmount = "(CashAmount * InterestRate ) / (fixedLeg.notional.token.currencyCode.equals('GBP')) ? 365 : 360";
    }
    return CommonViewModel;
}());
exports.CommonViewModel = CommonViewModel;
//# sourceMappingURL=CommonViewModel.js.map