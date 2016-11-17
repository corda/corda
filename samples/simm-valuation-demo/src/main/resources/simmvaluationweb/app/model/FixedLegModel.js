"use strict";
var FixedLegModel = (function () {
    function FixedLegModel() {
        this.fixedRatePayer = null;
        this.notional = {
            token: ""
        };
        this.paymentFrequency = null;
        this.effectiveDate = null;
        this.terminationDate = null;
        this.fixedRate = null;
        this.dayCountBasisDay = null;
        this.dayCountBasisYear = null;
        this.rollConvention = null;
        this.dayInMonth = null;
        this.paymentRule = null;
        this.paymentCalendar = null;
        this.interestPeriodAdjustment = null;
    }
    return FixedLegModel;
}());
exports.FixedLegModel = FixedLegModel;
//# sourceMappingURL=FixedLegModel.js.map