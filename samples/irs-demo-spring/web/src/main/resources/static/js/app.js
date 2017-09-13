"use strict";

function formatDateForNode(date) {
    // Produces yyyy-dd-mm. JS is missing proper date formatting libs
    let day = ("0" + (date.getDate())).slice(-2);
    let month = ("0" + (date.getMonth() + 1)).slice(-2);
    return `${date.getFullYear()}-${month}-${day}`;
}

function formatDateForAngular(dateStr) {
    let parts = dateStr.split("-");
    return new Date(parts[0], parts[1], parts[2]);
}

define([
    'angular',
    'angularRoute',
    'jquery',
    'fcsaNumber',
    'semantic'
], (angular, angularRoute, $, fcsaNumber, semantic) => {
    angular.module('irsViewer', ['ngRoute', 'fcsa-number']);
    requirejs(['routes']);
});