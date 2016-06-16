"use strict"

let irsViewer = angular.module('irsViewer', ['ngRoute'])
    .config(($routeProvider, $locationProvider) => {
        $routeProvider
            .when('/', {
                controller: 'HomeController',
                templateUrl: 'view/home.html'
            })
            .when('/deal/:dealId', {
                controller: 'DealController',
                templateUrl: 'view/deal.html'
            })
            .when('/party/:partyId', {
                templateUrl: 'view/party.html'
            })
            .when('/create-deal', {
                templateUrl: 'view/create-deal.html'
            })
            .otherwise({redirectTo: '/'});
    })

let nodeService = irsViewer.factory('nodeService', ($http) => {
    return new (function() {
        var date = new Date(2016, 0, 1, 0, 0, 0);
        var curLoading = {};

        var load = (type, promise) => {
            curLoading[type] = true;
            return promise.then((arg) => {
                curLoading[type] = false;
                return arg;
            }, (arg) => {
                curLoading[type] = false;
                throw arg;
            });
        }

        var changeDateOnNode = (newDate) => {
            // Produces yyyy-dd-mm. JS is missing proper date formatting libs
            const dateStr = newDate.toISOString().substring(0, 10);
            return load('date', $http.put('http://localhost:31338/api/irs/demodate', "\"" + dateStr + "\"")).then((resp) => {
                date = newDate;
                return this.getDateModel(date);
            });
        }

        this.getDate = () => {
            return load('date', $http.get('http://localhost:31338/api/irs/demodate')).then((resp) => {
                const parts = resp.data.split("-");
                date = new Date(parts[0], parts[1] - 1, parts[2]); // JS uses 0 based months
                return this.getDateModel(date);
            });
        }

        this.updateDate = (type) => {
            let newDate = date;
            switch(type) {
                case "year":
                    newDate.setFullYear(date.getFullYear() + 1);
                    break;

                case "month":
                    newDate.setMonth(date.getMonth() + 1);
                    break;

                case "day":
                    newDate.setDate(date.getDate() + 1);
                    break;
            }

            return changeDateOnNode(newDate);
        };

        this.getDeals = () => {
            return load('deals', $http.get('http://localhost:31338/api/irs/deals')).then((resp) => {
                return resp.data;
            });
        };

        this.getDeal = (dealId) => {
            return load('deal' + dealId, $http.get('http://localhost:31338/api/irs/deals/' + dealId)).then((resp) => {
                // Do some data modification to simplify the model
                let deal = resp.data;
                deal.fixedLeg.fixedRate.value = (deal.fixedLeg.fixedRate.ratioUnit.value * 100).toString().slice(0, 6);
                console.log(deal);
                return deal;
            });
        };

        this.getDateModel = (date) => {
            return {
                "year": date.getFullYear(),
                "month": date.getMonth() + 1, // JS uses 0 based months
                "day": date.getDate()
            };
        }

        this.isLoading = () => {
            return _.reduce(Object.keys(curLoading), (last, key) => {
                return (last || curLoading[key]);
            }, false);
        }
    });
});

irsViewer.controller('HomeController', function HomeController($http, $scope, nodeService) {
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

irsViewer.controller('DealController', function DealController($http, $scope, nodeService) {
    let initSemanticUi = () => {
        $('.ui.accordion').accordion();
    }

    initSemanticUi();

    $scope.isLoading = nodeService.isLoading;

    nodeService.getDeal('T000000001').then((deal) => $scope.deal = deal);
});