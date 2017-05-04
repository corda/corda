'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi', 'services/HttpErrorHandler'], (angular, semantic) => {
    angular.module('irsViewer').controller('DealController', function DealController($http, $scope, $routeParams, nodeService, httpErrorHandler) {
        semantic.init($scope, nodeService.isLoading);
        let handleHttpFail = httpErrorHandler.createErrorHandler($scope);
        nodeService.getDeal($routeParams.dealId).then((deal) => $scope.deal = deal, handleHttpFail);
    });
});