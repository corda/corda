import { Injectable, EventEmitter } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Http } from '@angular/http';
import { environment } from './environment';
import { Observable } from 'rxjs/Rx';

@Injectable()
export class HttpWrapperService {

  private counterparty: string;

  constructor(private http: Http, private router: Router) {
    // because components listen on newCounterparty,
    // they need to know there is a new value when view is switched
    router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) { //NavigationEnd?
        this.emitNewCounterparty();
      }
    });
  }

  //new CP events

  private emitNewCounterparty() {
    if (this.counterparty) {
      this.newCounterparty.emit({
        value: this.counterparty
      });
    }
  }
  public newCounterparty: EventEmitter < any > = new EventEmitter();

  // end new CP events


  // CP getter and setter

  public setCounterparty(cp) {
    this.counterparty = cp;
    this.emitNewCounterparty();
    return cp; //chainable
  }
  public getCounterparty() {
    return this.counterparty;
  }

  // end CP getter and setter


  // HTTP helpers

  private getPath(resource) {
    return environment.APIPath + resource;
  }

  // end HTTP helpers


  // HTTP methods

  public getWithCounterparty(resource): any {
    return this.http.get(this.getPath(this.counterparty + "/" + resource)).map(res => res.json());
  }

  public postWithCounterparty(resource, data): any {
    return this.http.post(this.getPath(this.counterparty + "/" + resource), data).map(res => res.json());
  }

  public putWithCounterparty(resource, data): any {
    return this.http.put(this.getPath(this.counterparty + "/" + resource), data).map(res => res.json());
  }

  public getAbsolute(resource): any {
    return this.http.get(this.getPath(resource)).map(res => res.json());
  }

  // end HTTP methods



  // *****************************************
  // Demo magic - delayed data for valuations
  // *****************************************

  private subscription;
  private step: number = 0;
  private updateDelayedData(data) {
    if (!data.portfolio) {
      return; // data hasn't fully returned yet, don't do anything
    }

    var delayedData: any = {};

    if(this.step > 0) {
      delayedData.portfolio = data.portfolio;
      delayedData.portfolio.agreed = (this.step > 1);
    }

    if(this.step > 2) {
      delayedData.marketData = data.marketData;
      delayedData.marketData.agreed = (this.step > 3);
    }

    if(this.step > 4) {
      delayedData.sensitivities = data.sensitivities;
      delayedData.sensitivities.agreed = (this.step > 5);
    }

    if(this.step > 6) {
      delayedData.initialMargin = data.initialMargin;
      delayedData.initialMargin.agreed = (this.step > 7);
    }

    if(this.step > 8) {
      delayedData.confirmation = data.confirmation;
      delayedData.confirmation.agreed = (this.step > 9);
    }

    if(this.step == 10) {
      this.subscription.unsubscribe();
    }
    return delayedData;
  }

  public startDelayedTimer() {
    this.step = 0;

    // every x second, update data
    let timer = Observable.timer(1000, 2000);
    this.subscription = timer.subscribe(t => { this.step++; });
  }

  public getDelayedData(data): any {
    return this.updateDelayedData(data)
  }

  // *****************************************
  // end demo magic
  // *****************************************
}
