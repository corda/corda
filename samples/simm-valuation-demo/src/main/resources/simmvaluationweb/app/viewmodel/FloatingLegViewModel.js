"use strict";
var FloatingLegViewModel = (function () {
    function FloatingLegViewModel() {
        this.floatingRatePayer = "CN=Bank B,O=Bank B,L=New York,C=US";
        this.notional = {
            quantity: 2500000000
        };
        this.paymentFrequency = "Quarterly";
        this.dayCountBasis = "ACT/360";
        this.rollConvention = "ModifiedFollowing";
        this.fixingRollConvention = "ModifiedFollowing";
        this.dayInMonth = 10;
        this.resetDayInMonth = 10;
        this.paymentRule = "InArrears";
        this.paymentDelay = "0";
        this.interestPeriodAdjustment = "Adjusted";
        this.fixingPeriodOffset = 2;
        this.resetRule = "InAdvance";
        this.fixingsPerPayment = "Quarterly";
        this.indexSource = "Rates Service Provider";
        this.indexTenor = {
            name: "3M"
        };
    }
    return FloatingLegViewModel;
}());
exports.FloatingLegViewModel = FloatingLegViewModel;
//# sourceMappingURL=FloatingLegViewModel.js.map