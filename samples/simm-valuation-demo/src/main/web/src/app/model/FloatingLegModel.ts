export class FloatingLegModel {
  floatingRatePayer: string = null;
  notional = {
    token: ""
  };
  paymentFrequency: string = null;
  effectiveDate: string = null;
  terminationDate: string = null;
  dayCountBasisDay: string = null;
  dayCountBasisYear: string = null;
  rollConvention: string = null;
  fixingRollConvention: string = null;
  dayInMonth: string = null;
  resetDayInMonth: string = null;
  paymentRule: string = null;
  paymentDelay: string = null;
  interestPeriodAdjustment: string = null;
  fixingPeriodOffset: string = null;
  resetRule: string = null;
  fixingsPerPayment: string = null;
  indexSource: string = null;
  index: string = null;
  indexTenor = {
    name: ""
  };
  fixingCalendar: string[] = [];
  paymentCalendar: string[] = [];
}
