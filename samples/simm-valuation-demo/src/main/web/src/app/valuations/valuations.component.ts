/* beautify preserve:start */
declare var $;
/* beautify preserve:end */

import { Component, OnInit } from '@angular/core';
import { HttpWrapperService } from '../http-wrapper.service';
import { Observable } from 'rxjs/Rx';

@Component({
  moduleId: module.id,
  selector: 'app-valuations',
  templateUrl: 'valuations.component.html',
  styleUrls: ['valuations.component.css'],
  directives: []
})
export class ValuationsComponent implements OnInit {
  private data: any = {};
  private formattedData: any = {
    sensitivitiesCurves: []
  };
  private fullData: any = {};
  private businessDate: string;
  private timer;
  private timerSubscription;
  private counterpartySubscription;

  // show loading spinner when clicked and data is not all received
  private calculateClicked: boolean;

  private startCalculations() {
    console.log("Starting calculations")
    this.fullData = {};
    this.data = {}; // outdated data, delete it
    this.calculateClicked = true; // show loading spinners

    // demo magic - this is to ensure we use the right valuation date
    this.httpWrapperService.postWithCounterparty("portfolio/valuations/calculate", { valuationDate: "2016-06-06" } )
      .toPromise().then((data) => {
        this.fullData = data;
        this.businessDate = data.businessDate; // in case it's valuations for a different date now
        this.httpWrapperService.startDelayedTimer(); // demo magic
        this.getData();
      });
  }

  private getData() {
    this.data = this.httpWrapperService.getDelayedData(this.fullData);

    if (this.data && this.data.sensitivities) {
      this.formattedData.sensitivitiesCurves = this.getSensitivitiesCurves(this.data.sensitivities);
    }

    // scroll to bottom of page
    let spinners = document.getElementById("loadingSpinners");
    if (spinners) {
      setTimeout(() => {
        $("html, body").animate({ scrollTop: $(document).height() }, 1000);
      }, 100); // wait for spinners to have gone below latest element
    }
  }

  // TODO: make this independent from the actual curve names
  private getSensitivitiesCurves(sensitivities) {
    let formattedSensitivities = []; // formattedSensitivities

    // loop on the first curve, knowing that the other curves have the same values
    for (let key in sensitivities.curves["EUR-DSCON-BIMM"]) {
      if (sensitivities.curves["EUR-DSCON-BIMM"].hasOwnProperty(key)) {
        let obj = {
          tenor: key, //3M, 6M etc...
          "EUR-DSCON-BIMM": sensitivities.curves["EUR-DSCON-BIMM"][key],
          "EUR-EURIBOR3M-BIMM": sensitivities.curves["EUR-EURIBOR3M-BIMM"][key]
        };
        formattedSensitivities.push(obj);
      }
    }

    return formattedSensitivities;
  }

  constructor(private httpWrapperService: HttpWrapperService) {}

  ngOnInit() {
    this.httpWrapperService.getAbsolute("business-date").toPromise().then((data) => {
      this.businessDate = data.businessDate;
    }).catch((error) => {
      console.log("Error loading business date", error);
    });

    // check for new data periodically
    // higher timeout because makes debugging annoying, put to 2000 for production
    this.timer = Observable.timer(0, 2000);
    this.timerSubscription = (this.timer.subscribe(() => this.getData()));

    // but also check for new data when counterparty changes
    this.counterpartySubscription = this.httpWrapperService.newCounterparty.subscribe((state) => {
      this.getData();
    });

  }

  ngOnDestroy() {
    this.timerSubscription.unsubscribe();
    this.counterpartySubscription.unsubscribe();
  }

}
