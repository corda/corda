"use strict";

function formatDateForNode(date) {
    // Produces yyyy-dd-mm. JS is missing proper date formatting libs
    var day = ("0" + date.getDate()).slice(-2);
    var month = ("0" + (date.getMonth() + 1)).slice(-2);
    return date.getFullYear() + "-" + month + "-" + day;
}

function formatDateForAngular(dateStr) {
    var parts = dateStr.split("-");
    return new Date(parts[0], parts[1], parts[2]);
}

define(['angular', 'angularRoute', 'jquery', 'fcsaNumber', 'semantic'], function (angular, angularRoute, $, fcsaNumber, semantic) {
    angular.module('irsViewer', ['ngRoute', 'fcsa-number']);
    requirejs(['routes']);
});