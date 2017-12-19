'use strict';

define(['angular', 'controllers/Home', 'controllers/Deal', 'controllers/CreateDeal'], function (angular) {
    angular.module('irsViewer').config(function ($routeProvider, $locationProvider) {
        $routeProvider.when('/', {
            controller: 'HomeController',
            templateUrl: 'view/home.html'
        }).when('/deal/:dealId', {
            controller: 'DealController',
            templateUrl: 'view/deal.html'
        }).when('/party/:partyId', {
            templateUrl: 'view/party.html'
        }).when('/create-deal', {
            controller: 'CreateDealController',
            templateUrl: 'view/create-deal.html'
        }).otherwise({ redirectTo: '/' });
    });

    angular.element().ready(function () {
        // bootstrap the app manually
        angular.bootstrap(document, ['irsViewer']);
    });
});