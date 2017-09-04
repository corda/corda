'use strict';

define(['angular', 'lodash', 'viewmodel/Deal'], (angular, _, dealViewModel) => {
    angular.module('irsViewer').factory('nodeService', ($http) => {
        return new (function() {
            let date = new Date(2016, 0, 1, 0, 0, 0);
            let curLoading = {};
            let serverAddr = ''; // Leave empty to target the same host this page is served from

            let load = (type, promise) => {
                curLoading[type] = true;
                return promise.then((arg) => {
                    curLoading[type] = false;
                    return arg;
                }, (arg) => {
                    curLoading[type] = false;
                    throw arg;
                });
            };

            let endpoint = (target) => serverAddr + target;

            let changeDateOnNode = (newDate) => {
                const dateStr = formatDateForNode(newDate);
                return load('date', $http.put(endpoint('/api/irs/demodate'), "\"" + dateStr + "\"")).then((resp) => {
                    date = newDate;
                    return this.getDateModel(date);
                });
            };

            this.getDate = () => {
                return load('date', $http.get(endpoint('/api/irs/demodate'))).then((resp) => {
                    const dateParts = resp.data;
                    date = new Date(dateParts[0], dateParts[1] - 1, dateParts[2]); // JS uses 0 based months
                    return this.getDateModel(date);
                });
            };

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
                return load('deals', $http.get(endpoint('/api/irs/deals'))).then((resp) => {
                    return resp.data.reverse();
                });
            };

            this.getDeal = (dealId) => {
                return load('deal' + dealId, $http.get(endpoint('/api/irs/deals/' + dealId))).then((resp) => {
                    // Do some data modification to simplify the model
                    let deal = resp.data;
                    deal.fixedLeg.fixedRate.value = (deal.fixedLeg.fixedRate.ratioUnit.value * 100).toString().slice(0, 6);
                    return deal;
                });
            };

            this.getDateModel = (date) => {
                return {
                    "year": date.getFullYear(),
                    "month": date.getMonth() + 1, // JS uses 0 based months
                    "day": date.getDate()
                };
            };

            this.isLoading = () => {
                return _.reduce(Object.keys(curLoading), (last, key) => {
                    return (last || curLoading[key]);
                }, false);
            };

            this.newDeal = () => {
                return dealViewModel;
            };

            this.createDeal = (deal) => {
                return load('create-deal', $http.post(endpoint('/api/irs/deals'), deal.toJson()))
                .then((resp) => {
                    return deal.tradeId;
                }, (resp) => {
                    throw resp;
                })
            }
        });
    });
});