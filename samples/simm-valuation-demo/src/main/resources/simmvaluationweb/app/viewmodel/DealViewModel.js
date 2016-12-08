"use strict";
var FixedLegViewModel_1 = require('./FixedLegViewModel');
var FloatingLegViewModel_1 = require('./FloatingLegViewModel');
var CommonViewModel_1 = require('./CommonViewModel');
var DealViewModel = (function () {
    function DealViewModel() {
        this.fixedLeg = new FixedLegViewModel_1.FixedLegViewModel();
        this.floatingLeg = new FloatingLegViewModel_1.FloatingLegViewModel();
        this.common = new CommonViewModel_1.CommonViewModel();
    }
    return DealViewModel;
}());
exports.DealViewModel = DealViewModel;
//# sourceMappingURL=DealViewModel.js.map