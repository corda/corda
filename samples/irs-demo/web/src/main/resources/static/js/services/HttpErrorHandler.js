'use strict';

define(['angular', 'lodash', 'viewmodel/Deal'], (angular, _) => {
    angular.module('irsViewer').factory('httpErrorHandler', () => {
        return {
            createErrorHandler: (scope) => {
                return (resp) => {
                    if(resp.status == -1) {
                        scope.httpError = "Could not connect to node web server";
                    } else {
                        scope.httpError = resp.data;
                    }
                };
            }
        };
    });
});