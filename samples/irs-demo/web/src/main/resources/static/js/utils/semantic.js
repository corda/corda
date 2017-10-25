'use strict';

define(['jquery', 'semantic'], ($, semantic) => {
    return {
        init: function($scope, loadingFunc) {
            $('.ui.accordion').accordion();
            $('.ui.dropdown').dropdown();
            $('.ui.sticky').sticky();

            this.addLoadingModal($scope, loadingFunc);
        },
        addLoadingModal: ($scope, loadingFunc) => {
            $scope.$watch(loadingFunc, (newVal) => {
                if(newVal === true) {
                    $('#loading').modal('setting', 'closable', false).modal('show');
                } else {
                    $('#loading').modal('hide');
                }
            });
        }
    };
});