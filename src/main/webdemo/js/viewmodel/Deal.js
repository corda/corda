'use strict';

define(['viewmodel/fixedLeg', 'viewmodel/floatingLeg', 'viewmodel/common'], (fixedLeg, floatingLeg, common) => {
    return {
        fixedLeg: fixedLeg,
        floatingLeg: floatingLeg,
        common: common
    };
});