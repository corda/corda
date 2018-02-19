'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi', 'services/HttpErrorHandler'], function (angular, semantic) {
    angular.module('irsViewer').controller('HomeController', function HomeController($http, $scope, nodeService, httpErrorHandler) {
        semantic.addLoadingModal($scope, nodeService.isLoading);

        var handleHttpFail = httpErrorHandler.createErrorHandler($scope);

        $scope.infoMsg = "";
        $scope.errorText = "";
        $scope.date = { "year": "...", "month": "...", "day": "..." };
        $scope.updateDate = function (type) {
            nodeService.updateDate(type).then(function (newDate) {
                $scope.date = newDate;
            }, handleHttpFail);
        };
        /* Extract the common name from an X500 name */
        $scope.renderX500Name = function (x500Name) {
            var name = x500Name;
            x500Name.split(',').forEach(function (element) {
                var keyValue = element.split('=');
                if (keyValue[0].toUpperCase() == 'CN') {
                    name = keyValue[1];
                }
            });
            return name;
        };

        nodeService.getDate().then(function (date) {
            return $scope.date = date;
        }, handleHttpFail);
        nodeService.getDeals().then(function (deals) {
            return $scope.deals = deals;
        }, handleHttpFail);
    });
});