'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi'], (angular, semantic, nodeApi) => {
    angular.module('irsViewer').controller('DealController', function DealController($http, $scope, $routeParams, nodeService) {
        semantic.init($scope, nodeService.isLoading);

        nodeService.getDeal($routeParams.dealId).then((deal) => $scope.deal = deal);
    });
});