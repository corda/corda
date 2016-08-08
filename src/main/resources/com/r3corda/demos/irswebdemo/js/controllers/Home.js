'use strict';

define(['angular', 'utils/semantic', 'services/NodeApi'], (angular, semantic, nodeApi) => {
    angular.module('irsViewer').controller('HomeController', function HomeController($http, $scope, nodeService) {
        semantic.addLoadingModal($scope, nodeService.isLoading);

        let handleHttpFail = (resp) => {
            $scope.httpError = resp.data
        };

        $scope.infoMsg = "";
        $scope.errorText = "";
        $scope.date = { "year": "...", "month": "...", "day": "..." };
        $scope.updateDate = (type) => {
            nodeService.updateDate(type).then((newDate) => {
                $scope.date = newDate
            }, handleHttpFail);
        };

        nodeService.getDate().then((date) => $scope.date = date);
        nodeService.getDeals().then((deals) => $scope.deals = deals);
    });
});