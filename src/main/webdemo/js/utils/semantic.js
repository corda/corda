'use strict';

define(['jquery', 'semantic'], ($, semantic) => {
    return {
        init: () => {
            $('.ui.accordion').accordion();
            $('.ui.dropdown').dropdown();
            $('.ui.sticky').sticky();
        }
    };
});