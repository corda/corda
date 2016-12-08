import { DealViewModel } from './viewmodel/DealViewModel';
import { NodeService } from './node.service';
import { IRSService } from './irs.service';
import { FixedLegModel } from './model/FixedLegModel';
import { FloatingLegModel } from './model/FloatingLegModel';
import { CommonModel } from './model/CommonModel';
import { FixedLegViewModel } from './viewmodel/FixedLegViewModel';
import { FloatingLegViewModel } from './viewmodel/FloatingLegViewModel';
import { CommonViewModel } from './viewmodel/CommonViewModel';
import * as _ from 'underscore';

let calculationModel = {
  expression: "( fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.quantity * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
  floatingLegPaymentSchedule: {

  },
  fixedLegPaymentSchedule: {

  }
};

let fixedRateModel = {
  ratioUnit: {
    value: 0.01 // %
  }
};

let indexLookup = {
  "GBP": "ICE LIBOR",
  "USD": "ICE LIBOR",
  "EUR": "EURIBOR"
};

let calendarLookup = {
  "GBP": "London",
  "USD": "NewYork",
  "EUR": "London"
};

let now = () => {
  return new Date();
}

// Copy the value of the field from b to a if it exists on both objects.
let unionMerge = (a, b) => {
  for (let key in b) {
    if (a.hasOwnProperty(key)) {
      a[key] = b[key];
    }
  }
}

export class Deal {
  tradeId = `T${now().getUTCFullYear()}-${now().getUTCMonth()}-${now().getUTCDate()}.${now().getUTCHours()}:${now().getUTCMinutes()}:${now().getUTCSeconds()}:${now().getUTCMilliseconds()}`

  constructor(private dealViewModel: DealViewModel, private nodeService: NodeService, private irsService: IRSService) {}

  toFixedLegModel = (fixedLegVM: FixedLegViewModel, commonVM: CommonViewModel) => {
    let fixedLeg = new FixedLegModel();

    unionMerge(fixedLeg, fixedLegVM);

    fixedLeg.notional.token = commonVM.baseCurrency;
    fixedLeg.effectiveDate = commonVM.effectiveDate;
    fixedLeg.terminationDate = commonVM.terminationDate;
    fixedLeg.fixedRate = { ratioUnit: { value: Number(fixedLegVM.fixedRate) / 100 } };
    fixedLeg.dayCountBasisDay = this.irsService.lookupDayCountBasis(fixedLegVM.dayCountBasis).day;
    fixedLeg.dayCountBasisYear = this.irsService.lookupDayCountBasis(fixedLegVM.dayCountBasis).year;
    fixedLeg.paymentCalendar = calendarLookup[commonVM.baseCurrency];

    return fixedLeg;
  };

  toFloatingLegModel = (floatingLegVM: FloatingLegViewModel, commonVM: CommonViewModel) => {
    let floatingLeg = new FloatingLegModel();

    unionMerge(floatingLeg, floatingLegVM);

    floatingLeg.notional.token = commonVM.baseCurrency;
    floatingLeg.effectiveDate = commonVM.effectiveDate;
    floatingLeg.terminationDate = commonVM.terminationDate;
    floatingLeg.dayCountBasisDay = this.irsService.lookupDayCountBasis(floatingLegVM.dayCountBasis).day;
    floatingLeg.dayCountBasisYear = this.irsService.lookupDayCountBasis(floatingLegVM.dayCountBasis).year;
    floatingLeg.index = indexLookup[commonVM.baseCurrency];
    floatingLeg.fixingCalendar = [calendarLookup[commonVM.baseCurrency]];
    floatingLeg.paymentCalendar = [calendarLookup[commonVM.baseCurrency]];

    return floatingLeg;
  };

  toCommonModel = (commonVM: CommonViewModel) => {
    let common = new CommonModel();

    unionMerge(common, commonVM);

    common.tradeID = this.tradeId;
    common.eligibleCurrency = commonVM.baseCurrency;
    common.independentAmounts.token = commonVM.baseCurrency;
    common.threshold.token = commonVM.baseCurrency;
    common.minimumTransferAmount.token = commonVM.baseCurrency;
    common.rounding.token = commonVM.baseCurrency;

    return common;
  };

  toJson = () => {
    let commonVM = this.dealViewModel.common;
    let floatingLegVM = this.dealViewModel.floatingLeg;
    let fixedLegVM = this.dealViewModel.fixedLeg;

    let fixedLeg = this.toFixedLegModel(fixedLegVM, commonVM);
    let floatingLeg = this.toFloatingLegModel(floatingLegVM, commonVM);
    let common = this.toCommonModel(commonVM);
    _.assign(fixedLeg.fixedRate, fixedRateModel);

    let json = {
      fixedLeg: fixedLeg,
      floatingLeg: floatingLeg,
      calculation: calculationModel,
      common: common
    }

    return json;
  };
};
