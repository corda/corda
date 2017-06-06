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
        /* Extract the common name from an X500 name */
        $scope.renderX500Name = (x500Name) => {
            var name = x500Name
            x500Name.split(',').forEach(function(element) {
                var keyValue = element.split('=');
                if (keyValue[0].toUpperCase() == 'CN') {
                    name = keyValue[1];
                }
            });
            return name;
        };

        nodeService.getDate().then((date) => $scope.date = date);
        nodeService.getDeals().then((deals) => $scope.deals = deals);
    });
});
