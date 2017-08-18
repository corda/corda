"use strict";
var CommonModel = (function () {
    function CommonModel() {
        this.baseCurrency = null;
        this.eligibleCreditSupport = null;
        this.independentAmounts = {
            token: ""
        };
        this.threshold = {
            token: ""
        };
        this.minimumTransferAmount = {
            token: ""
        };
        this.rounding = {
            token: ""
        };
        this.valuationDate = null;
        this.notificationTime = null;
        this.resolutionTime = null;
        this.interestRate = null;
        this.addressForTransfers = null;
        this.exposure = null;
        this.localBusinessDay = null;
        this.dailyInterestAmount = null;
        this.tradeID = null;
    }
    return CommonModel;
}());
exports.CommonModel = CommonModel;
//# sourceMappingURL=CommonModel.js.map