"use strict";
function __export(m) {
    for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];
}
var select_1 = require('./components/select');
__export(require('./components/select/select'));
__export(require('./components/select'));
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = {
    directives: [
        select_1.SELECT_DIRECTIVES
    ]
};
