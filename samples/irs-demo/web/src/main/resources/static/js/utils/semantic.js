'use strict';

define(['jquery', 'semantic'], function ($, semantic) {
    return {
        init: function init($scope, loadingFunc) {
            $('.ui.accordion').accordion();
            $('.ui.dropdown').dropdown();
            $('.ui.sticky').sticky();

            this.addLoadingModal($scope, loadingFunc);
        },
        addLoadingModal: function addLoadingModal($scope, loadingFunc) {
            $scope.$watch(loadingFunc, function (newVal) {
                if (newVal === true) {
                    $('#loading').modal('setting', 'closable', false).modal('show');
                } else {
                    $('#loading').modal('hide');
                }
            });
        }
    };
});