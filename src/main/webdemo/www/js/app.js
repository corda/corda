"use strict"

var irsViewer = angular.module('irsViewer', [])

irsViewer.controller('HomeController', ($http, $scope) => {
    $http.get('http://localhost:31338/api/irs/deals').then((resp) => {
        $scope.deals = resp.data
    })
})