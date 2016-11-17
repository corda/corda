"use strict";
var FloatingLegModel = (function () {
    function FloatingLegModel() {
        this.floatingRatePayer = null;
        this.notional = {
            token: ""
        };
        this.paymentFrequency = null;
        this.effectiveDate = null;
        this.terminationDate = null;
        this.dayCountBasisDay = null;
        this.dayCountBasisYear = null;
        this.rollConvention = null;
        this.fixingRollConvention = null;
        this.dayInMonth = null;
        this.resetDayInMonth = null;
        this.paymentRule = null;
        this.paymentDelay = null;
        this.interestPeriodAdjustment = null;
        this.fixingPeriodOffset = null;
        this.resetRule = null;
        this.fixingsPerPayment = null;
        this.indexSource = null;
        this.index = null;
        this.indexTenor = {
            name: ""
        };
        this.fixingCalendar = [];
        this.paymentCalendar = [];
    }
    return FloatingLegModel;
}());
exports.FloatingLegModel = FloatingLegModel;
//# sourceMappingURL=FloatingLegModel.js.map