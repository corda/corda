'use strict';

define(['angular', 'lodash', 'viewmodel/Deal'], function (angular, _, dealViewModel) {
    angular.module('irsViewer').factory('nodeService', function ($http) {
        return new function () {
            var _this = this;

            var date = new Date(2016, 0, 1, 0, 0, 0);
            var curLoading = {};
            var serverAddr = ''; // Leave empty to target the same host this page is served from

            var load = function load(type, promise) {
                curLoading[type] = true;
                return promise.then(function (arg) {
                    curLoading[type] = false;
                    return arg;
                }, function (arg) {
                    curLoading[type] = false;
                    throw arg;
                });
            };

            var endpoint = function endpoint(target) {
                return serverAddr + target;
            };

            var changeDateOnNode = function changeDateOnNode(newDate) {
                var dateStr = formatDateForNode(newDate);
                return load('date', $http.put(endpoint('/api/irs/demodate'), "\"" + dateStr + "\"")).then(function (resp) {
                    date = newDate;
                    return _this.getDateModel(date);
                });
            };

            this.getDate = function () {
                return load('date', $http.get(endpoint('/api/irs/demodate'))).then(function (resp) {
                    var dateParts = resp.data;
                    date = new Date(dateParts[0], dateParts[1] - 1, dateParts[2]); // JS uses 0 based months
                    return _this.getDateModel(date);
                });
            };

            this.updateDate = function (type) {
                var newDate = date;
                switch (type) {
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

            this.getDeals = function () {
                return load('deals', $http.get(endpoint('/api/irs/deals'))).then(function (resp) {
                    return resp.data.reverse();
                });
            };

            this.getDeal = function (dealId) {
                return load('deal' + dealId, $http.get(endpoint('/api/irs/deals/' + dealId))).then(function (resp) {
                    // Do some data modification to simplify the model
                    var deal = resp.data;
                    deal.fixedLeg.fixedRate.value = (deal.fixedLeg.fixedRate.ratioUnit.value * 100).toString().slice(0, 6);
                    return deal;
                });
            };

            this.getDateModel = function (date) {
                return {
                    "year": date.getFullYear(),
                    "month": date.getMonth() + 1, // JS uses 0 based months
                    "day": date.getDate()
                };
            };

            this.isLoading = function () {
                return _.reduce(Object.keys(curLoading), function (last, key) {
                    return last || curLoading[key];
                }, false);
            };

            this.newDeal = function () {
                return dealViewModel;
            };

            this.createDeal = function (deal) {
                return load('create-deal', $http.post(endpoint('/api/irs/deals'), deal.toJson())).then(function (resp) {
                    return deal.tradeId;
                }, function (resp) {
                    throw resp;
                });
            };
        }();
    });
});