export class FloatingLegViewModel {
  constructor() { }

  floatingRatePayer = "Bank B";
  notional: Object = {
     quantity: 2500000000
  };
  paymentFrequency = "Quarterly";
  effectiveDateAdjustment: any;
  terminationDateAdjustment: any;
  dayCountBasis = "ACT/360";
  rollConvention = "ModifiedFollowing";
  fixingRollConvention = "ModifiedFollowing";
  dayInMonth: Number = 10;
  resetDayInMonth: Number = 10;
  paymentRule = "InArrears";
  paymentDelay = "0";
  interestPeriodAdjustment = "Adjusted";
  fixingPeriodOffset: Number = 2;
  resetRule = "InAdvance";
  fixingsPerPayment = "Quarterly";
  indexSource = "Rates Service Provider";
  indexTenor = {
     name: "3M"
  };
}
