'use strict';

define(['angular', 'maskedInput', 'utils/semantic', 'utils/dayCountBasisLookup', 'services/NodeApi', 'Deal', 'services/HttpErrorHandler'], function (angular, maskedInput, semantic, dayCountBasisLookup, nodeApi, Deal) {
    angular.module('irsViewer').controller('CreateDealController', function CreateDealController($http, $scope, $location, nodeService, httpErrorHandler) {
        semantic.init($scope, nodeService.isLoading);
        var handleHttpFail = httpErrorHandler.createErrorHandler($scope);

        $scope.dayCountBasisLookup = dayCountBasisLookup;
        $scope.deal = nodeService.newDeal();
        $scope.createDeal = function () {
            nodeService.createDeal(new Deal($scope.deal)).then(function (tradeId) {
                return $location.path('#/deal/' + tradeId);
            }, function (resp) {
                $scope.formError = resp.data;
            }, handleHttpFail);
        };
        $('input.percent').mask("9.999999", { placeholder: "", autoclear: false });
        $('#swapirscolumns').click(function () {
            var first = $('#irscolumns .irscolumn:eq( 0 )');
            var last = $('#irscolumns .irscolumn:eq( 1 )');
            first.before(last);

            var swapPayers = function swapPayers() {
                var tmp = $scope.deal.floatingLeg.floatingRatePayer;
                $scope.deal.floatingLeg.floatingRatePayer = $scope.deal.fixedLeg.fixedRatePayer;
                $scope.deal.fixedLeg.fixedRatePayer = tmp;
            };
            $scope.$apply(swapPayers);
        });
    });
});