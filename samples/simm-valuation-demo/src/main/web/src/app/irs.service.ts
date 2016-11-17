import { Injectable } from '@angular/core';

export class DayCountBasis {
  constructor(public day: string, public year: string) {}
}

@Injectable()
export class IRSService {
  lookupTable = {
    "30/360": new DayCountBasis("D30", "Y360"),
    "30E/360": new DayCountBasis("D30E", "Y360"),
    "ACT/360": new DayCountBasis("DActual", "Y360"),
    "ACT/365 Fixed": new DayCountBasis("DActual", "Y365F"),
    "ACT/365 L": new DayCountBasis("DActual", "Y365L"),
    "ACT/ACT ISDA": new DayCountBasis("DActual", "YISDA"),
    "ACT/ACT ICMA": new DayCountBasis("DActual", "YICMA")
  }

  constructor() {}

  lookupDayCountBasis: Function = (shorthand: string) => {
    return this.lookupTable[shorthand];
  }

}
