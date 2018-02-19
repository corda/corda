'use strict';

define(['angular', 'lodash', 'viewmodel/Deal'], function (angular, _) {
    angular.module('irsViewer').factory('httpErrorHandler', function () {
        return {
            createErrorHandler: function createErrorHandler(scope) {
                return function (resp) {
                    if (resp.status == -1) {
                        scope.httpError = "Could not connect to node web server";
                    } else {
                        scope.httpError = resp.data;
                    }
                };
            }
        };
    });
});