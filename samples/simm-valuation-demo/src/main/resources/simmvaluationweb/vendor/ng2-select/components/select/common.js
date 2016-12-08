"use strict";
function escapeRegexp(queryToEscape) {
    return queryToEscape.replace(/([.?*+^$[\]\\(){}|-])/g, '\\$1');
}
exports.escapeRegexp = escapeRegexp;
