"use strict"

let irsViewer = angular.module('irsViewer', []);

let nodeService = irsViewer.factory('nodeService', ($http) => {
    return new (function() {
        var date = new Date(2016, 0, 1, 0, 0, 0);

        var changeDateOnNode = (newDate) => {
            // Produces yyyy-dd-mm. JS is missing proper date formatting libs
            const dateStr = newDate.toISOString().substring(0, 10);
            return $http.put('http://localhost:31338/api/irs/demodate', "\"" + dateStr + "\"").then((resp) => {
                date = newDate;
            });
        }

        this.getDate = () => {
            return {
                 year: date.getFullYear(),
                 month: date.getMonth() + 1, // Month is zero indexed
                 day: date.getDate()
             };
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
    });
});

irsViewer.controller('HomeController', ($http, $scope, nodeService) => {
    let handleHttpFail = (resp) => {
        console.log(resp.data)
        $scope.httpError = resp.data
    }

    $scope.errorText = "";
    $scope.date = nodeService.getDate();
    $scope.updateDate = (type) => { nodeService.updateDate(type).then((newDate) => {$scope.date = newDate}, handleHttpFail) };

    $http.get('http://localhost:31338/api/irs/deals').then((resp) => {
        $scope.deals = resp.data;
    });
})