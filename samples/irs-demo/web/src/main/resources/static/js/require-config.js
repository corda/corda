'use strict';

require.config({
	paths: {
		angular: 'bower_components/angular/angular',
		angularRoute: 'bower_components/angular-route/angular-route',
		fcsaNumber: 'bower_components/angular-fcsa-number/src/fcsaNumber',
		jquery: 'bower_components/jquery/jquery',
		semantic: 'bower_components/semantic/semantic',
		lodash: 'bower_components/lodash/lodash',
		maskedInput: 'bower_components/jquery.maskedinput/jquery.maskedinput'
	},
	shim: {
		'angular': { 'exports': 'angular' },
		'angularRoute': ['angular'],
		'fcsaNumber': ['angular'],
		'semantic': ['jquery'],
		'maskedInput': ['jquery']
	},
	priority: ["angular"],
	baseUrl: 'js'
});

require(['angular', 'app'], function (angular, app) {});