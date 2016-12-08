// not really the Angular way:
/* beautify preserve:start */
declare var $;
declare var Highcharts;
/* beautify preserve:end */

import { Component, OnInit } from '@angular/core';
import { TAB_DIRECTIVES } from 'ng2-bootstrap/ng2-bootstrap';
import { POPOVER_DIRECTIVES } from 'ng2-popover';
import { FORM_DIRECTIVES } from '@angular/forms/src/directives' // https://github.com/valor-software/ng2-bootstrap/issues/782
import { CORE_DIRECTIVES, NgClass, NgIf } from '@angular/common';
import { PAGINATION_DIRECTIVES } from 'ng2-bootstrap/ng2-bootstrap';
import { NG_TABLE_DIRECTIVES } from 'ng2-table/ng2-table';
import { HttpWrapperService } from '../http-wrapper.service';
import { Router } from '@angular/router';
import { Observable } from 'rxjs/Rx';

@Component({
  moduleId: module.id,
  selector: 'app-portfolio',
  templateUrl: 'portfolio.component.html',
  styleUrls: [
    'portfolio.component.css'
  ],
  directives: [POPOVER_DIRECTIVES, TAB_DIRECTIVES, NG_TABLE_DIRECTIVES, PAGINATION_DIRECTIVES, NgIf, CORE_DIRECTIVES, FORM_DIRECTIVES]
})

export class PortfolioComponent implements OnInit {

  private IDFormatter(id) {
    return "<a href='/view-trade/" + id + "'>" + id + "</a>";
  }

  private defaultFormatter(value) {
    return value;
  }

  private numberFormatter(n) {
    if (!n) {
      return "";
    }

    var a = "" + n;
    a = a.replace(new RegExp("^(\\d{" + (a.length % 3 ? a.length % 3 : 0) + "})(\\d{3})", "g"), "$1 $2").replace(/(\d{3})+?/gi, "$1 ").trim();
    var sep = ",";
    a = a.replace(/\s/g, sep);

    return a;
  }

  public rows: Array < any > = [];
  public columns: Array < any > = [
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
  public page: number = 1;
  public itemsPerPage: number = 10;
  public maxSize: number = 5;
  public numPages: number = 1;
  public length: number = 0;

  public config: any = {
    paging: true,
    sorting: { columns: this.columns }
  };

  private businessDate: string;

  private data: Array < any > = [];

  private summaryTable: any = {
    product: "Vanilla IRS",
    currency: "EUR",
    trades: 0,
    notional: 0,
    im: 0,
    mtm: 0
  };

  private createTradesChart(TData) {
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
  }

  private createIMOverVMChart(IMVMData) {
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
  }

  private createIMVMHistoryChart(IMData, MTMData) {
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
        dateTimeLabelFormats: { // don't display the dummy year
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
  }

  private createActiveTradesChart(ATData) {
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
        dateTimeLabelFormats: { // don't display the dummy year
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
  }

  private createIMVMHistorySummaryChart(IMData, MTMData) {
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
  }

  constructor(private httpWrapperService: HttpWrapperService, private router: Router) {}

  private getData() {
    if (this.httpWrapperService.getCounterparty()) {
      // re-initialize addittive table sums
      this.summaryTable.trades = 0;
      this.summaryTable.notional = 0;
      this.summaryTable.im = 0;
      this.summaryTable.mtm = 0;

      this.data = null; //don't leave old data in case of errors

      //trades
      this.httpWrapperService.getWithCounterparty("trades").toPromise().then((data) => {

        // trades over time scatter
        var TData = [];

        // trades IM over VM scatter
        var IMVMData = [];

        $.each(data, (index, value) => {
          if (value.margined) {
            TData.push([new Date(value.tradeDate).getTime(), value.im]);

            IMVMData.push([value.im, value.mtm]);
          }
        });

        this.createTradesChart(TData);

        this.createIMOverVMChart(IMVMData);

        // trades table
        this.data = data;
        this.length = this.data.length;
        this.onChangeTable(this.config);

      }).catch((error) => {
        console.log("Error loading trades", error);
      });

      this.populateSummary().then(() => {
        // portfolio history and active trades charts
        this.httpWrapperService.getWithCounterparty("portfolio/history/aggregated").toPromise().then((data) => {
          // summary table
          let lastDay = data
          this.summaryTable.trades = lastDay.activeTrades;
          this.summaryTable.notional = lastDay.notional;
          this.summaryTable.im = lastDay.im;
          this.summaryTable.mtm = lastDay.mtm;

          var IMData = [];
          var MTMData = [];
          var ATData = [];

          $.each(data, (index, value) => {
            // new Date(value.date).getTime() when dates are switched to YYYY-MM-DD
            IMData.push([value.date, value.im]);
            MTMData.push([value.date, value.mtm]);
            ATData.push([value.date, value.activeTrades]);
          });

          this.createIMVMHistoryChart(IMData, MTMData);
          this.createActiveTradesChart(ATData);

          this.createIMVMHistorySummaryChart(IMData, MTMData);
        }).catch((error) => {
          console.log("Error loading portfolio history", error);
        })
      })
    }
  }

  private populateSummary() {
    return this.httpWrapperService.getWithCounterparty("portfolio/summary").toPromise().then((data) => {
      this.summaryTable.trades = data.trades;
      this.summaryTable.notional = data.notional;
    }).catch((error) => {
      console.log("Error loading portfolio summary", error);
    })
  }

  counterpartySubscription: any;
  ngOnInit() {
    this.httpWrapperService.getAbsolute("business-date").toPromise().then((data) => {
      this.businessDate = data.businessDate;
    }).catch((error) => {
      console.log("Error loading business date", error);
    });

    Highcharts.setOptions({
      lang: {
        thousandsSep: ','
      }
    });

    this.getData();
    this.counterpartySubscription = this.httpWrapperService.newCounterparty.subscribe((state) => {
      this.getData();
    });
  }

  ngOnDestroy() {
    this.counterpartySubscription.unsubscribe();
  }

  // table helper functions

  public changePage(page: any, data: Array < any > = this.data): Array < any > {
    let start = (page.page - 1) * page.itemsPerPage;
    let end = page.itemsPerPage > -1 ? (start + page.itemsPerPage) : data.length;
    return data.slice(start, end);
  }

  public changeSort(data: any, config: any): any {
    if (!config.sorting) {
      return data;
    }

    let columns = this.config.sorting.columns || [];
    let columnName: string = void 0;
    let sort: string = void 0;

    for (let i = 0; i < columns.length; i++) {
      if (columns[i].sort !== '') {
        columnName = columns[i].name;
        sort = columns[i].sort;
      }
    }

    if (!columnName) {
      return data;
    }

    // simple sorting
    return data.sort((previous: any, current: any) => {
      if (previous[columnName] > current[columnName]) {
        return sort === 'desc' ? -1 : 1;
      } else if (previous[columnName] < current[columnName]) {
        return sort === 'asc' ? -1 : 1;
      }
      return 0;
    });
  }

  public onChangeTable(config: any, page: any = { page: this.page, itemsPerPage: this.itemsPerPage }): any {
    if (config.sorting) {
      Object.assign(this.config.sorting, config.sorting);
    }

    let sortedData = this.changeSort(this.data, this.config);
    this.rows = page && config.paging ? this.changePage(page, sortedData) : sortedData;
    this.length = sortedData.length;
  }

  // end table helper functions

}
