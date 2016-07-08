'use strict';

define(['angular', 'services/NodeApi'], (angular, nodeApi) => {
    angular.module('irsViewer').controller('HomeController', function HomeController($http, $scope, nodeService) {
        let handleHttpFail = (resp) => {
            console.log(resp.data)
            $scope.httpError = resp.data
        }

        $scope.isLoading = nodeService.isLoading;
        $scope.infoMsg = "";
        $scope.errorText = "";
        $scope.date = { "year": "...", "month": "...", "day": "..." };
        $scope.updateDate = (type) => { nodeService.updateDate(type).then((newDate) => {$scope.date = newDate}, handleHttpFail); };

        nodeService.getDate().then((date) => $scope.date = date);
        nodeService.getDeals().then((deals) => $scope.deals = deals);
    });
})