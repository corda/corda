"use strict";
var FixedLegViewModel = (function () {
    function FixedLegViewModel() {
        this.fixedRatePayer = "CN=Bank A,O=Bank A,L=London,C=GB";
        this.notional = {
            quantity: 2500000000
        };
        this.paymentFrequency = "SemiAnnual";
        this.fixedRate = "1.676";
        this.dayCountBasis = "ACT/360";
        this.rollConvention = "ModifiedFollowing";
        this.dayInMonth = 10;
        this.paymentRule = "InArrears";
        this.paymentDelay = "0";
        this.interestPeriodAdjustment = "Adjusted";
    }
    return FixedLegViewModel;
}());
exports.FixedLegViewModel = FixedLegViewModel;
//# sourceMappingURL=FixedLegViewModel.js.map
