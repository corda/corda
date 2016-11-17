// Angular-CLI build configuration
// This file lists all the node_modules files that will be used in a build
// Also see https://github.com/angular/angular-cli/wiki/3rd-party-libs

/* global require, module */

var Angular2App = require('angular-cli/lib/broccoli/angular2-app');

module.exports = function(defaults) {
  return new Angular2App(defaults, {
    vendorNpmFiles: [
      'systemjs/dist/system-polyfills.js',
      'systemjs/dist/system.src.js',
      'zone.js/dist/**/*.+(js|js.map)',
      'es6-shim/es6-shim.js',
      'reflect-metadata/**/*.+(ts|js|js.map)',
      'rxjs/**/*.+(js|js.map)',
      '@angular/**/*.+(js|js.map)',
      'moment/moment.js',
      'underscore/underscore.js',
      'bootstrap/dist/css/*.css',
      'bootstrap/dist/fonts/*',
      'jquery/dist/jquery.min.js',
      'highcharts/highstock.js',
      'ng2-bootstrap/**/*.js',
      'ng2-popover/**/*.js',
      'ng2-select/**/*.js',
      'ng2-select/**/*.css',
      'ng2-table/**/*.js',
      'font-awesome/fonts/*.***',
      'font-awesome/css/font-awesome.min.css'
    ]
  });
};
