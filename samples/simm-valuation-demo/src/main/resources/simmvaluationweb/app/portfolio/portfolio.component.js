"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
/* beautify preserve:end */
var core_1 = require('@angular/core');
var ng2_bootstrap_1 = require('ng2-bootstrap/ng2-bootstrap');
var ng2_popover_1 = require('ng2-popover');
var directives_1 = require('@angular/forms/src/directives'); // https://github.com/valor-software/ng2-bootstrap/issues/782
var common_1 = require('@angular/common');
var ng2_bootstrap_2 = require('ng2-bootstrap/ng2-bootstrap');
var ng2_table_1 = require('ng2-table/ng2-table');
var http_wrapper_service_1 = require('../http-wrapper.service');
var router_1 = require('@angular/router');
var PortfolioComponent = (function () {
    function PortfolioComponent(httpWrapperService, router) {
        this.httpWrapperService = httpWrapperService;
        this.router = router;
        this.rows = [];
        this.columns = [
            // omitting the sort column on each column would result in no default sorting!
            { title: 'ID', name: 'id', sort: '', formatter: this.IDFormatter },
            { title: 'Product', name: 'product', sort: '', formatter: this.defaultFormatter },
            { title: 'Type', name: 'buySell', sort: '', formatter: this.defaultFormatter },
            { title: 'Trade Date', name: 'tradeDate', sort: 'desc', formatter: this.defaultFormatter },
            { title: 'Effective Date', name: 'effectiveDate', sort: '', formatter: this.defaultFormatter },
            { title: 'Maturity Date', name: 'maturityDate', sort: '', formatter: this.defaultFormatter },
            { title: 'Currency', name: 'currency', sort: '', formatter: this.defaultFormatter },
            { title: 'Notional', name: 'notional', sort: '', formatter: this.numberFormatter },
            { title: 'IM Contribution', name: 'im', sort: '', formatter: this.numberFormatter },
            { title: 'PV', name: 'mtm', sort: '', formatter: this.numberFormatter },
            { title: 'Included in summary', name: 'marginedText', sort: '', formatter: this.defaultFormatter }
        ];
        this.page = 1;
        this.itemsPerPage = 10;
        this.maxSize = 5;
        this.numPages = 1;
        this.length = 0;
        this.config = {
            paging: true,
            sorting: { columns: this.columns }
        };
        this.data = [];
        this.summaryTable = {
            product: "Vanilla IRS",
            currency: "EUR",
            trades: 0,
            notional: 0,
            im: 0,
            mtm: 0
        };
    }
    PortfolioComponent.prototype.IDFormatter = function (id) {
        return "<a href='/view-trade/" + id + "'>" + id + "</a>";
    };
    PortfolioComponent.prototype.defaultFormatter = function (value) {
        return value;
    };
    PortfolioComponent.prototype.numberFormatter = function (n) {
        if (!n) {
            return "";
        }
        var a = "" + n;
        a = a.replace(new RegExp("^(\\d{" + (a.length % 3 ? a.length % 3 : 0) + "})(\\d{3})", "g"), "$1 $2").replace(/(\d{3})+?/gi, "$1 ").trim();
        var sep = ",";
        a = a.replace(/\s/g, sep);
        return a;
    };
    PortfolioComponent.prototype.createTradesChart = function (TData) {
        var TFormat = 'Date: <b>{point.x:%Y-%m-%d}<b/><br>' + 'IM: <b>{point.y:,.0f}€<b/>';
        $('#tradesChart').highcharts('StockChart', {
            credits: {
                enabled: false
            },
            chart: {
                type: 'scatter',
                zoomType: 'xy'
            },
            rangeSelector: {
                selected: 4
            },
            title: {
                text: 'Individual Trades'
            },
            legend: {
                enabled: true
            },
            yAxis: {
                title: {
                    text: 'IM'
                }
            },
            series: [{
                    name: 'Trade',
                    data: TData,
                    tooltip: {
                        pointFormat: TFormat
                    }
                }]
        });
    };
    PortfolioComponent.prototype.createIMOverVMChart = function (IMVMData) {
        // note there's no "highstocks"
        $('#IMOverVMChart').highcharts({
            credits: {
                enabled: false
            },
            chart: {
                type: 'scatter',
                zoomType: 'xy'
            },
            title: {
                text: 'Imminent IM over Variation Margin of trades'
            },
            legend: {
                enabled: true
            },
            subtitle: {
                text: ''
            },
            xAxis: {
                title: {
                    enabled: true,
                    text: 'MTM'
                },
                startOnTick: true,
                endOnTick: true,
                showLastLabel: true
            },
            yAxis: {
                title: {
                    text: 'IM'
                }
            },
            plotOptions: {
                scatter: {
                    marker: {
                        radius: 5,
                        states: {
                            hover: {
                                enabled: true,
                                lineColor: 'rgb(100,100,100)'
                            }
                        }
                    },
                    states: {
                        hover: {
                            marker: {
                                enabled: false
                            }
                        }
                    },
                    tooltip: {
                        headerFormat: '<b>{series.name}</b><br>',
                        pointFormat: 'IM: {point.x:,.0f}€ <br> MTM: {point.x:,.0f}€ <br/>'
                    }
                }
            },
            series: [{
                    name: 'Trade',
                    data: IMVMData
                }]
        });
    };
    PortfolioComponent.prototype.createIMVMHistoryChart = function (IMData, MTMData) {
        $('#IMVMHistoryChart').highcharts('StockChart', {
            credits: {
                enabled: false
            },
            legend: {
                enabled: true
            },
            rangeSelector: {
                selected: 4
            },
            title: {
                text: 'Portfolio History'
            },
            subtitle: {
                text: 'Initial and Variation Margin Requirements'
            },
            xAxis: {
                type: 'datetime',
                dateTimeLabelFormats: {
                    //day: '%d'
                    month: '%e. %b',
                    year: '%b'
                },
                title: {
                    text: 'Date'
                }
            },
            yAxis: {
                title: {
                    text: 'Exposure (€)'
                },
                min: 0
            },
            plotOptions: {
                spline: {
                    marker: {
                        enabled: true
                    }
                }
            },
            series: [{
                    name: 'Initial Margin',
                    data: IMData,
                    type: 'column'
                }, {
                    name: 'Mark to Market',
                    data: MTMData,
                    type: 'spline'
                }]
        });
    };
    PortfolioComponent.prototype.createActiveTradesChart = function (ATData) {
        var ATformat = 'Active trades: <b>{point.y:,.0f}</b><br>' +
            'IM: <b>{point.x:,.0f}</b><br>';
        $('#activeTradesChart').highcharts('StockChart', {
            credits: {
                enabled: false
            },
            rangeSelector: {
                selected: 4
            },
            legend: {
                enabled: true
            },
            xAxis: {
                type: 'datetime',
                dateTimeLabelFormats: {
                    //day: '%d'
                    month: '%e. %b',
                    year: '%b'
                },
                title: {
                    text: 'Date'
                }
            },
            yAxis: {
                title: {
                    text: 'Quantity'
                }
            },
            title: {
                text: 'Active Trades'
            },
            series: [{
                    name: 'Active trades',
                    data: ATData,
                    tooltip: {
                        pointFormat: ATformat
                    }
                }]
        });
    };
    PortfolioComponent.prototype.createIMVMHistorySummaryChart = function (IMData, MTMData) {
        $('#IMVMHistorySummaryChart').highcharts('StockChart', {
            credits: {
                enabled: false
            },
            rangeSelector: {
                enabled: false
            },
            navigator: {
                enabled: false
            },
            scrollbar: {
                enabled: false
            },
            title: {
                text: 'Portfolio History'
            },
            legend: {
                enabled: true
            },
            xAxis: {
                type: 'datetime',
                title: {
                    text: 'Date'
                }
            },
            yAxis: {
                title: {
                    text: 'Exposure (€)'
                },
                min: 0
            },
            plotOptions: {
                spline: {
                    marker: {
                        enabled: true
                    }
                }
            },
            dataGrouping: {
                approximation: "average",
                enabled: true,
                forced: true,
                units: [
                    ['month', [1]]
                ]
            },
            series: [{
                    name: 'Initial Margin',
                    data: IMData,
                    type: 'column'
                }, {
                    name: 'Mark to Market',
                    data: MTMData,
                    type: 'spline'
                }]
        });
    };
    PortfolioComponent.prototype.getData = function () {
        var _this = this;
        if (this.httpWrapperService.getCounterparty()) {
            // re-initialize addittive table sums
            this.summaryTable.trades = 0;
            this.summaryTable.notional = 0;
            this.summaryTable.im = 0;
            this.summaryTable.mtm = 0;
            this.data = null; //don't leave old data in case of errors
            //trades
            this.httpWrapperService.getWithCounterparty("trades").toPromise().then(function (data) {
                // trades over time scatter
                var TData = [];
                // trades IM over VM scatter
                var IMVMData = [];
                $.each(data, function (index, value) {
                    if (value.margined) {
                        TData.push([new Date(value.tradeDate).getTime(), value.im]);
                        IMVMData.push([value.im, value.mtm]);
                    }
                });
                _this.createTradesChart(TData);
                _this.createIMOverVMChart(IMVMData);
                // trades table
                _this.data = data;
                _this.length = _this.data.length;
                _this.onChangeTable(_this.config);
            }).catch(function (error) {
                console.log("Error loading trades", error);
            });
            this.populateSummary().then(function () {
                // portfolio history and active trades charts
                _this.httpWrapperService.getWithCounterparty("portfolio/history/aggregated").toPromise().then(function (data) {
                    // summary table
                    var lastDay = data;
                    _this.summaryTable.trades = lastDay.activeTrades;
                    _this.summaryTable.notional = lastDay.notional;
                    _this.summaryTable.im = lastDay.im;
                    _this.summaryTable.mtm = lastDay.mtm;
                    var IMData = [];
                    var MTMData = [];
                    var ATData = [];
                    $.each(data, function (index, value) {
                        // new Date(value.date).getTime() when dates are switched to YYYY-MM-DD
                        IMData.push([value.date, value.im]);
                        MTMData.push([value.date, value.mtm]);
                        ATData.push([value.date, value.activeTrades]);
                    });
                    _this.createIMVMHistoryChart(IMData, MTMData);
                    _this.createActiveTradesChart(ATData);
                    _this.createIMVMHistorySummaryChart(IMData, MTMData);
                }).catch(function (error) {
                    console.log("Error loading portfolio history", error);
                });
            });
        }
    };
    PortfolioComponent.prototype.populateSummary = function () {
        var _this = this;
        return this.httpWrapperService.getWithCounterparty("portfolio/summary").toPromise().then(function (data) {
            _this.summaryTable.trades = data.trades;
            _this.summaryTable.notional = data.notional;
        }).catch(function (error) {
            console.log("Error loading portfolio summary", error);
        });
    };
    PortfolioComponent.prototype.ngOnInit = function () {
        var _this = this;
        this.httpWrapperService.getAbsolute("business-date").toPromise().then(function (data) {
            _this.businessDate = data.businessDate;
        }).catch(function (error) {
            console.log("Error loading business date", error);
        });
        Highcharts.setOptions({
            lang: {
                thousandsSep: ','
            }
        });
        this.getData();
        this.counterpartySubscription = this.httpWrapperService.newCounterparty.subscribe(function (state) {
            _this.getData();
        });
    };
    PortfolioComponent.prototype.ngOnDestroy = function () {
        this.counterpartySubscription.unsubscribe();
    };
    // table helper functions
    PortfolioComponent.prototype.changePage = function (page, data) {
        if (data === void 0) { data = this.data; }
        var start = (page.page - 1) * page.itemsPerPage;
        var end = page.itemsPerPage > -1 ? (start + page.itemsPerPage) : data.length;
        return data.slice(start, end);
    };
    PortfolioComponent.prototype.changeSort = function (data, config) {
        if (!config.sorting) {
            return data;
        }
        var columns = this.config.sorting.columns || [];
        var columnName = void 0;
        var sort = void 0;
        for (var i = 0; i < columns.length; i++) {
            if (columns[i].sort !== '') {
                columnName = columns[i].name;
                sort = columns[i].sort;
            }
        }
        if (!columnName) {
            return data;
        }
        // simple sorting
        return data.sort(function (previous, current) {
            if (previous[columnName] > current[columnName]) {
                return sort === 'desc' ? -1 : 1;
            }
            else if (previous[columnName] < current[columnName]) {
                return sort === 'asc' ? -1 : 1;
            }
            return 0;
        });
    };
    PortfolioComponent.prototype.onChangeTable = function (config, page) {
        if (page === void 0) { page = { page: this.page, itemsPerPage: this.itemsPerPage }; }
        if (config.sorting) {
            Object.assign(this.config.sorting, config.sorting);
        }
        var sortedData = this.changeSort(this.data, this.config);
        this.rows = page && config.paging ? this.changePage(page, sortedData) : sortedData;
        this.length = sortedData.length;
    };
    PortfolioComponent = __decorate([
        core_1.Component({
            moduleId: module.id,
            selector: 'app-portfolio',
            templateUrl: 'portfolio.component.html',
            styleUrls: [
                'portfolio.component.css'
            ],
            directives: [ng2_popover_1.POPOVER_DIRECTIVES, ng2_bootstrap_1.TAB_DIRECTIVES, ng2_table_1.NG_TABLE_DIRECTIVES, ng2_bootstrap_2.PAGINATION_DIRECTIVES, common_1.NgIf, common_1.CORE_DIRECTIVES, directives_1.FORM_DIRECTIVES]
        }), 
        __metadata('design:paramtypes', [http_wrapper_service_1.HttpWrapperService, router_1.Router])
    ], PortfolioComponent);
    return PortfolioComponent;
}());
exports.PortfolioComponent = PortfolioComponent;
//# sourceMappingURL=portfolio.component.js.map