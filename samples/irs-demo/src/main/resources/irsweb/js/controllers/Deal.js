'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi', 'services/HttpErrorHandler'], (angular, semantic) => {
    angular.module('irsViewer').controller('DealController', function DealController($http, $scope, $routeParams, nodeService, httpErrorHandler) {
        semantic.init($scope, nodeService.isLoading);
        let handleHttpFail = httpErrorHandler.createErrorHandler($scope);
        let decorateDeal = (deal) => {
            let paymentSchedule = deal.calculation.floatingLegPaymentSchedule;
            Object.keys(paymentSchedule).map((key, index) => {
                const sign = paymentSchedule[key].rate.positive ? 1 : -1;
                paymentSchedule[key].ratePercent = paymentSchedule[key].rate.ratioUnit ? (paymentSchedule[key].rate.ratioUnit.value * 100 * sign).toFixed(5) + "%": "";
            });

            return deal;
        };

        nodeService.getDeal($routeParams.dealId).then((deal) => $scope.deal = decorateDeal(deal), handleHttpFail);
    });
});