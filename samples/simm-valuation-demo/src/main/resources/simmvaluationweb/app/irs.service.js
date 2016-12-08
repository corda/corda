"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var core_1 = require('@angular/core');
var DayCountBasis = (function () {
    function DayCountBasis(day, year) {
        this.day = day;
        this.year = year;
    }
    return DayCountBasis;
}());
exports.DayCountBasis = DayCountBasis;
var IRSService = (function () {
    function IRSService() {
        var _this = this;
        this.lookupTable = {
            "30/360": new DayCountBasis("D30", "Y360"),
            "30E/360": new DayCountBasis("D30E", "Y360"),
            "ACT/360": new DayCountBasis("DActual", "Y360"),
            "ACT/365 Fixed": new DayCountBasis("DActual", "Y365F"),
            "ACT/365 L": new DayCountBasis("DActual", "Y365L"),
            "ACT/ACT ISDA": new DayCountBasis("DActual", "YISDA"),
            "ACT/ACT ICMA": new DayCountBasis("DActual", "YICMA")
        };
        this.lookupDayCountBasis = function (shorthand) {
            return _this.lookupTable[shorthand];
        };
    }
    IRSService = __decorate([
        core_1.Injectable(), 
        __metadata('design:paramtypes', [])
    ], IRSService);
    return IRSService;
}());
exports.IRSService = IRSService;
//# sourceMappingURL=irs.service.js.map