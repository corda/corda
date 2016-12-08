"use strict";
var __extends = (this && this.__extends) || function (d, b) {
    for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p];
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
};
var FutureAction_1 = require('./FutureAction');
var QueueScheduler_1 = require('./QueueScheduler');
var AsyncScheduler = (function (_super) {
    __extends(AsyncScheduler, _super);
    function AsyncScheduler() {
        _super.apply(this, arguments);
    }
    AsyncScheduler.prototype.scheduleNow = function (work, state) {
        return new FutureAction_1.FutureAction(this, work).schedule(state, 0);
    };
    return AsyncScheduler;
}(QueueScheduler_1.QueueScheduler));
exports.AsyncScheduler = AsyncScheduler;
//# sourceMappingURL=AsyncScheduler.js.map