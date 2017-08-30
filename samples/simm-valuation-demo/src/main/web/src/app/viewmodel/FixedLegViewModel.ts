export class FixedLegViewModel {
  constructor() { }

  fixedRatePayer = "CN=Bank A,O=Bank A,L=London,C=GB";
  notional: Object = {
      quantity: 2500000000
  };
  paymentFrequency = "SemiAnnual";
  effectiveDateAdjustment: any;
  terminationDateAdjustment: any;
  fixedRate = "1.676";
  dayCountBasis = "ACT/360";
  rollConvention = "ModifiedFollowing";
  dayInMonth: Number = 10;
  paymentRule = "InArrears";
  paymentDelay = "0";
  interestPeriodAdjustment = "Adjusted";
}
