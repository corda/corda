export class FixedLegModel {
  fixedRatePayer: string = null;
  notional = {
    token: ""
  };
  paymentFrequency: string = null;
  effectiveDate: string = null;
  terminationDate: string = null;
  fixedRate: Object = null;
  dayCountBasisDay: string = null;
  dayCountBasisYear: string = null;
  rollConvention: string = null;
  dayInMonth: number = null;
  paymentRule: string = null;
  paymentCalendar: string = null;
  interestPeriodAdjustment: string = null;
}
