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
      fixedRate: {
        ratioUnit: {
            value: "0.01676"
        }
    },
    dayCountBasisDay: "D30",
    dayCountBasisYear: "Y360",
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
   dayCountBasisDay: "D30",
   dayCountBasisYear: "Y360",
   rollConvention: "ModifiedFollowing",
   fixingRollConvention: "ModifiedFollowing",
   dayInMonth: 10,
   resetDayInMonth: 10,
   paymentRule: "InArrears",
   paymentDelay: 0,
   paymentCalendar: [ "London" ],
   interestPeriodAdjustment: "Adjusted",
   fixingPeriod: "TWODAYS",
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

// Todo: Finish this model to solve the problem of the dates being in two different formats
let commonModel = function() {
    this.baseCurrency = "EUR";
    this.eligibleCurrency = "EUR";
    this.eligibleCreditSupport: "Cash in an Eligible Currency";
    this.independentAmounts = {
        quantity: 0,
        token: "EUR"
    };
    this.threshold = {
        quantity: 0,
        token: "EUR"
    };
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
    resolutionTime: "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given ",
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
    tradeID: tradeId,
    hashLegalDocs: "put hash here"
};

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
                controller: 'CreateDealController',
                templateUrl: 'view/create-deal.html'
            })
            .otherwise({redirectTo: '/'});
    })

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
            let now = new Date();
            let tradeId = `T${now.getUTCFullYear()}-${now.getUTCMonth()}-${now.getUTCDate()}.${now.getUTCHours()}:${now.getUTCMinutes()}:${now.getUTCSeconds()}:${now.getUTCMilliseconds()}`

            return {
                fixedLeg: fixedLegModel,
                floatingLeg: floatingLegModel,
                calculation: calculationModel,
                common: commonModel,
                notary: "Bank A"
              };
        }

        this.createDeal = (deal) => {
            return load('create-deal', $http.post('http://localhost:31338/api/irs/deals', deal))
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

function prepareDeal(deal) {
    let newDeal = Object.assign({}, deal);

    newDeal.fixedLeg.effectiveDate = formatDateForNode(newDeal.fixedLeg.effectiveDate);
    newDeal.fixedLeg.terminationDate = formatDateForNode(newDeal.fixedLeg.terminationDate);
    newDeal.floatingLeg.effectiveDate = formatDateForNode(newDeal.floatingLeg.effectiveDate);
    newDeal.floatingLeg.terminationDate = formatDateForNode(newDeal.floatingLeg.terminationDate);

    return newDeal;
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
        nodeService.createDeal(prepareDeal($scope.deal))
        .then((tradeId) => $location.path('#/deal/' + tradeId), (resp) => {
            $scope.formError = resp.data;
        });
    };
});