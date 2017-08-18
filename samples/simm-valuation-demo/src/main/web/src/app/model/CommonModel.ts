export class CommonModel {
  baseCurrency: string = null;
  eligibleCreditSupport: string = null;
  independentAmounts = {
    token: ""
  };
  threshold = {
    token: ""
  };
  minimumTransferAmount = {
    token: ""
  };
  rounding = {
    token: ""
  };
  valuationDate: string = null;
  notificationTime: string = null;
  resolutionTime: string = null;
  interestRate: Object = null;
  addressForTransfers: string = null;
  exposure: Object = null;
  localBusinessDay: Object = null;
  dailyInterestAmount: string = null;
  tradeID: string = null;
  eligibleCurrency: string;
}
