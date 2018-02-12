'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi', 'services/HttpErrorHandler'], function (angular, semantic) {
    angular.module('irsViewer').controller('DealController', function DealController($http, $scope, $routeParams, nodeService, httpErrorHandler) {
        semantic.init($scope, nodeService.isLoading);
        var handleHttpFail = httpErrorHandler.createErrorHandler($scope);
        var decorateDeal = function decorateDeal(deal) {
            var paymentSchedule = deal.calculation.floatingLegPaymentSchedule;
            Object.keys(paymentSchedule).map(function (key, index) {
                var sign = paymentSchedule[key].rate.positive ? 1 : -1;
                paymentSchedule[key].ratePercent = paymentSchedule[key].rate.ratioUnit ? (paymentSchedule[key].rate.ratioUnit.value * 100 * sign).toFixed(5) + "%" : "";
            });

            return deal;
        };

        nodeService.getDeal($routeParams.dealId).then(function (deal) {
            return $scope.deal = decorateDeal(deal);
        }, handleHttpFail);
    });
});