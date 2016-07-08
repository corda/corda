"use strict"

function formatDateForNode(date) {
    // Produces yyyy-dd-mm. JS is missing proper date formatting libs
    return date.toISOString().substring(0, 10);
}

function formatDateForAngular(dateStr) {
    let parts = dateStr.split("-");
    return new Date(parts[0], parts[1], parts[2]);
}

let fixedLegModel = {
    fixedRatePayer: "Bank A",
    notional: {
        quantity: 2500000000,
        token: "EUR"
    },
    paymentFrequency: "SemiAnnual",
    effectiveDate: new Date(2016, 3, 11),
    effectiveDateAdjustment: null,
    terminationDate: new Date(2026, 3, 11),
    terminationDateAdjustment: null,
    fixedRate: "1.676",
    dayCountBasis: "30/360",
    //dayCountBasisDay: "D30",
    //dayCountBasisYear: "Y360",
    rollConvention: "ModifiedFollowing",
    dayInMonth: 10,
    paymentRule: "InArrears",
    paymentDelay: 0,
    paymentCalendar: "London",
    interestPeriodAdjustment: "Adjusted"
};

let floatingLegModel = {
   floatingRatePayer: "Bank B",
   notional: {
       quantity: 2500000000,
       token: "EUR"
   },
   paymentFrequency: "Quarterly",
   effectiveDate: new Date(2016, 3, 11),
   effectiveDateAdjustment: null,
   terminationDate: new Date(2026, 3, 11),
   terminationDateAdjustment: null,
   dayCountBasis: "30/360",
   //dayCountBasisDay: "D30",
   //dayCountBasisYear: "Y360",
   rollConvention: "ModifiedFollowing",
   fixingRollConvention: "ModifiedFollowing",
   dayInMonth: 10,
   resetDayInMonth: 10,
   paymentRule: "InArrears",
   paymentDelay: 0,
   paymentCalendar: [ "London" ],
   interestPeriodAdjustment: "Adjusted",
   fixingPeriodOffset: 2,
   resetRule: "InAdvance",
   fixingsPerPayment: "Quarterly",
   fixingCalendar: [ "NewYork" ],
   index: "ICE LIBOR",
   indexSource: "Rates Service Provider",
   indexTenor: {
       name: "3M"
   }
};

let calculationModel = {
    expression: "( fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value)) -(floatingLeg.notional.quantity * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
    floatingLegPaymentSchedule: {

    },
    fixedLegPaymentSchedule: {

    }
};

let fixedRateViewModel = {
    ratioUnit: {
        value: 0.01 // %
    }
}

let commonViewModel = {
    baseCurrency: "EUR",
    eligibleCurrency: "EUR",
    eligibleCreditSupport: "Cash in an Eligible Currency",
    independentAmounts: {
        quantity: 0,
        token: "EUR"
    },
    threshold: {
        quantity: 0,
        token: "EUR"
    },
    minimumTransferAmount: {
        quantity: 25000000,
        token: "EUR"
    },
    rounding: {
        quantity: 1000000,
        token: "EUR"
    },
    valuationDate: "Every Local Business Day",
    notificationTime: "2:00pm London",
    resolutionTime: "2:00pm London time on the first LocalBusiness Day following the date on which the notice is give",
    interestRate: {
        oracle: "Rates Service Provider",
        tenor: {
            name: "6M"
        },
        ratioUnit: null,
        name: "EONIA"
    },
    addressForTransfers: "",
    exposure: {},
    localBusinessDay: [ "London" , "NewYork" ],
    dailyInterestAmount: "(CashAmount * InterestRate ) / (fixedLeg.notional.token.currencyCode.equals('GBP')) ? 365 : 360",
    hashLegalDocs: "put hash here"
};

let dealViewModel = {
  fixedLeg: fixedLegModel,
  floatingLeg: floatingLegModel,
  common: commonViewModel
};

// TODO: Fill out this lookup table and use it to inject into the view.
let dayCountBasisLookup = {
    "30/360": {
        "day": "D30",
        "year": "Y360"
    }
}

let Deal = function(dealViewModel) {
    let now = new Date();
    let tradeId = `T${now.getUTCFullYear()}-${now.getUTCMonth()}-${now.getUTCDate()}.${now.getUTCHours()}:${now.getUTCMinutes()}:${now.getUTCSeconds()}:${now.getUTCMilliseconds()}`

    this.toJson = () => {
        let fixedLeg = {};
        let floatingLeg = {};
        let common = {};
        _.assign(fixedLeg, dealViewModel.fixedLeg);
        _.assign(floatingLeg, dealViewModel.floatingLeg);
        _.assign(common, dealViewModel.common);
        _.assign(fixedLeg.fixedRate, fixedRateViewModel);

        fixedLeg.fixedRate = Number(fixedLeg.fixedRate) / 100;

        common.tradeID = tradeId;
        fixedLeg.effectiveDate = formatDateForNode(fixedLeg.effectiveDate);
        fixedLeg.terminationDate = formatDateForNode(fixedLeg.terminationDate);
        fixedLeg.fixedRate = { ratioUnit: { value: fixedLeg.fixedRate } };
        fixedLeg.dayCountBasisDay = dayCountBasisLookup[fixedLeg.dayCountBasis].day;
        fixedLeg.dayCountBasisYear = dayCountBasisLookup[fixedLeg.dayCountBasis].year;
        delete fixedLeg.dayCountBasis;

        floatingLeg.effectiveDate = formatDateForNode(floatingLeg.effectiveDate);
        floatingLeg.terminationDate = formatDateForNode(floatingLeg.terminationDate);
        floatingLeg.dayCountBasisDay = dayCountBasisLookup[floatingLeg.dayCountBasis].day;
        floatingLeg.dayCountBasisYear = dayCountBasisLookup[floatingLeg.dayCountBasis].year;
        delete floatingLeg.dayCountBasis;

        let json = {
            fixedLeg: fixedLeg,
            floatingLeg: floatingLeg,
            calculation: calculationModel,
            common: common
        }

        return json;
    };
};

define([
    'angular',
    'angularRoute',
    'jquery',
    'fcsaNumber',
    'semantic',
    'maskedInput',
    'lodash',
    'js/Deal'
],
(angular, angularRoute, $, fcsaNumber, semantic, maskedInput, _, Deal) => {
    Deal()
    let irsViewer = angular.module('irsViewer', ['ngRoute', 'fcsa-number'])
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
                    controller: 'CreateDealController',
                    templateUrl: 'view/create-deal.html'
                })
                .otherwise({redirectTo: '/'});
        });

    let nodeService = irsViewer.factory('nodeService', ($http) => {
        return new (function() {
            let date = new Date(2016, 0, 1, 0, 0, 0);
            let curLoading = {};

            let load = (type, promise) => {
                curLoading[type] = true;
                return promise.then((arg) => {
                    curLoading[type] = false;
                    return arg;
                }, (arg) => {
                    curLoading[type] = false;
                    throw arg;
                });
            }

            let changeDateOnNode = (newDate) => {
                const dateStr = formatDateForNode(newDate)
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

            this.newDeal = () => {
                return dealViewModel;
            }

            this.createDeal = (deal) => {
                return load('create-deal', $http.post('http://localhost:31338/api/irs/deals', deal.toJson()))
                .then((resp) => {
                    return deal.tradeId;
                }, (resp) => {
                    throw resp;
                })
            }
        });
    });

    function initSemanticUi() {
        $('.ui.accordion').accordion();
        $('.ui.dropdown').dropdown();
    }

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

    irsViewer.controller('DealController', function DealController($http, $scope, $routeParams, nodeService) {
        initSemanticUi();

        $scope.isLoading = nodeService.isLoading;

        nodeService.getDeal($routeParams.dealId).then((deal) => $scope.deal = deal);
    });


    irsViewer.controller('CreateDealController', function CreateDealController($http, $scope, $location, nodeService) {
        initSemanticUi();

        $scope.isLoading = nodeService.isLoading;
        $scope.deal = nodeService.newDeal();
        $scope.createDeal = () => {
            nodeService.createDeal(new Deal($scope.deal))
            .then((tradeId) => $location.path('#/deal/' + tradeId), (resp) => {
                $scope.formError = resp.data;
            });
        };
        $('input.percent').mask("9.999999%", {placeholder: "", autoclear: false});
    });

});