'use strict';

require.config({
	paths: {
		angular: 'bower_components/angular/angular',
		angularRoute: 'bower_components/angular-route/angular-route',
		fcsaNumber: 'bower_components/angular-fcsa-number/src/fcsaNumber',
		jquery: 'bower_components/jquery/dist/jquery',
		semantic: 'bower_components/semantic/dist/semantic',
		lodash: 'bower_components/lodash/lodash',
		maskedInput: 'bower_components/jquery.maskedinput/dist/jquery.maskedinput'
	},
	shim: {
		'angular' : {'exports' : 'angular'},
		'angularRoute': ['angular'],
		'fcsaNumber': ['angular'],
		'semantic': ['jquery'],
		'maskedInput': ['jquery']
	},
	priority: [
		"angular"
	],
	baseUrl: 'js',
});

require(['angular', 'app'], (angular, app) => {

});